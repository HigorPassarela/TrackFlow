package dev.higor.trackflow.worker.ableton;

import dev.higor.trackflow.contracts.AbletonAnalyzed.Track;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Reads an Ableton Live project ({@code .als}, gzip-compressed XML) in one streaming pass (design
 * D10). The format is not documented; the paths below were surveyed on a project saved by Live
 * 12.4 and match what Live 11 writes, except that Live 11 calls the main track {@code MasterTrack}.
 *
 * <pre>
 * Ableton[@Creator]                                   "Ableton Live 12.4.6"
 *   LiveSet
 *     Tracks
 *       AudioTrack | MidiTrack | GroupTrack | ReturnTrack [@Id]
 *         Name/EffectiveName[@Value]                  name shown in Live
 *         DeviceChain/DeviceChain/Devices/*           one element per device, e.g. Compressor2
 *         .../VstPluginInfo/PlugName[@Value]          plugins, anywhere in the track,
 *         .../Vst3PluginInfo/Name[@Value]             including inside racks
 *         .../AuPluginInfo/Name[@Value]
 *     MainTrack (Live 12) | MasterTrack (Live 11)
 *       DeviceChain/Mixer/Tempo/Manual[@Value]        "125"
 *       DeviceChain/Mixer/TimeSignature/Manual[@Value] encoded: 201 = 4/4 (see timeSignature)
 * </pre>
 *
 * The file is untrusted input: DTDs and external entities are disabled, so a declaration pointing
 * to a file or URL fails the parse instead of being fetched, and decompression stops at {@value
 * #MAX_UNCOMPRESSED_BYTES} bytes. Anything else the reader does not know is ignored.
 */
@Component
public class AbletonProjectReader {

    static final long MAX_UNCOMPRESSED_BYTES = 300L * 1024 * 1024;

    private static final Map<String, String> TRACK_TYPES =
            Map.of("AudioTrack", "audio", "MidiTrack", "midi", "GroupTrack", "group", "ReturnTrack", "return");
    private static final Set<String> MAIN_TRACKS = Set.of("MainTrack", "MasterTrack");
    private static final Set<String> PLUGIN_INFOS = Set.of("VstPluginInfo", "Vst3PluginInfo", "AuPluginInfo");
    private static final Set<String> PLUGIN_NAMES = Set.of("PlugName", "Name");
    private static final Pattern MAJOR_VERSION = Pattern.compile("^Ableton Live (\\d+)\\.");
    private static final Set<Integer> VERIFIED_MAJOR_VERSIONS = Set.of(11, 12);

    // Element depths: Ableton = 1, LiveSet = 2, Tracks and the main track = 3, a track = 4.
    private static final int MAIN_TRACK_DEPTH = 3;
    private static final int TRACK_DEPTH = 4;

    private final XMLInputFactory factory;

    public AbletonProjectReader() {
        factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    }

    /** @param als the file as stored, still gzip-compressed */
    public AbletonProject read(InputStream als) {
        try (InputStream xml = new LimitedInputStream(new GZIPInputStream(als), MAX_UNCOMPRESSED_BYTES)) {
            XMLStreamReader reader = factory.createXMLStreamReader(xml);
            try {
                return parse(reader);
            } finally {
                reader.close();
            }
        } catch (XMLStreamException | IOException | NumberFormatException e) {
            if (isLimitExceeded(e)) {
                throw new InvalidProjectException(
                        InvalidProjectException.PROJECT_FILE_TOO_LARGE, "Project expands beyond the limit", e);
            }
            throw new InvalidProjectException(
                    InvalidProjectException.INVALID_PROJECT_FILE, "Not a readable Ableton Live project", e);
        }
    }

    /** The parser wraps stream errors, so the limit can arrive several causes deep. */
    private static boolean isLimitExceeded(Throwable error) {
        for (Throwable cause = error; cause != null; cause = nextCause(cause)) {
            if (cause instanceof LimitExceededException) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable Throwable nextCause(Throwable error) {
        if (error instanceof XMLStreamException xml && xml.getNestedException() != null) {
            return xml.getNestedException();
        }
        return error.getCause();
    }

    private static AbletonProject parse(XMLStreamReader reader) throws XMLStreamException {
        List<String> path = new ArrayList<>();
        List<Track> tracks = new ArrayList<>();
        @Nullable String creator = null;
        @Nullable Double tempo = null;
        @Nullable String timeSignature = null;
        @Nullable TrackBuilder track = null;
        boolean inMainTrack = false;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = reader.getLocalName();
                path.add(name);
                int depth = path.size();
                String value = reader.getAttributeValue(null, "Value");

                if (depth == 1 && name.equals("Ableton")) {
                    creator = reader.getAttributeValue(null, "Creator");
                } else if (depth == TRACK_DEPTH
                        && isUnder(path, "LiveSet", "Tracks")
                        && TRACK_TYPES.containsKey(name)) {
                    track = new TrackBuilder(reader.getAttributeValue(null, "Id"), TRACK_TYPES.get(name));
                } else if (depth == MAIN_TRACK_DEPTH && path.get(1).equals("LiveSet") && MAIN_TRACKS.contains(name)) {
                    inMainTrack = true;
                } else if (track != null) {
                    track.visit(path.subList(TRACK_DEPTH, depth), value);
                } else if (inMainTrack && value != null) {
                    List<String> relative = path.subList(MAIN_TRACK_DEPTH, depth);
                    if (relative.equals(List.of("DeviceChain", "Mixer", "Tempo", "Manual"))) {
                        tempo = Double.valueOf(value);
                    } else if (relative.equals(List.of("DeviceChain", "Mixer", "TimeSignature", "Manual"))) {
                        timeSignature = timeSignature(Integer.parseInt(value));
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                int depth = path.size();
                if (depth == TRACK_DEPTH && track != null) {
                    tracks.add(track.build());
                    track = null;
                } else if (depth == MAIN_TRACK_DEPTH) {
                    inMainTrack = false;
                }
                path.removeLast();
            }
        }
        if (creator == null) {
            throw new XMLStreamException("Not an Ableton Live project: no Ableton root element");
        }
        return new AbletonProject(creator, isVerified(creator), tempo, timeSignature, tracks);
    }

    private static boolean isUnder(List<String> path, String liveSet, String tracks) {
        return path.get(0).equals("Ableton")
                && path.get(1).equals(liveSet)
                && path.get(2).equals(tracks);
    }

    /**
     * Live stores the signature as one number: {@code numerator - 1 + 99 * log2(denominator)}, so
     * 4/4 is 3 + 99 * 2 = 201.
     */
    static String timeSignature(int encoded) {
        int numerator = encoded % 99 + 1;
        int denominator = 1 << (encoded / 99);
        return numerator + "/" + denominator;
    }

    private static boolean isVerified(String creator) {
        Matcher matcher = MAJOR_VERSION.matcher(creator);
        return matcher.find() && VERIFIED_MAJOR_VERSIONS.contains(Integer.parseInt(matcher.group(1)));
    }

    /** Collects one track while the reader is inside it. */
    private static final class TrackBuilder {

        private static final List<String> DEVICES = List.of("DeviceChain", "DeviceChain", "Devices");

        private final String id;
        private final String type;
        private String name = "";
        private final Set<String> devices = new LinkedHashSet<>();
        private final Set<String> plugins = new LinkedHashSet<>();

        private TrackBuilder(@Nullable String id, String type) {
            this.id = id != null ? id : "";
            this.type = type;
        }

        /** @param relative path below the track element, ending with the current element */
        void visit(List<String> relative, @Nullable String value) {
            String element = relative.getLast();
            if (relative.equals(List.of("Name", "EffectiveName")) && value != null) {
                name = value;
            } else if (relative.size() == DEVICES.size() + 1
                    && relative.subList(0, DEVICES.size()).equals(DEVICES)) {
                devices.add(element);
            } else if (relative.size() >= 2
                    && PLUGIN_INFOS.contains(relative.get(relative.size() - 2))
                    && PLUGIN_NAMES.contains(element)
                    && value != null
                    && !value.isBlank()) {
                plugins.add(value);
            }
        }

        Track build() {
            return new Track(id, type, name, List.copyOf(devices), List.copyOf(plugins));
        }
    }

    /** Stops the decompression once the limit is passed, so a small file cannot expand forever. */
    private static final class LimitedInputStream extends FilterInputStream {

        private final long limit;
        private long count;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                count(read);
            }
            return read;
        }

        private void count(long bytes) throws IOException {
            count += bytes;
            if (count > limit) {
                throw new LimitExceededException(limit);
            }
        }
    }

    /** An {@link IOException} so it travels through the XML parser, which wraps it. */
    private static final class LimitExceededException extends IOException {

        LimitExceededException(long limit) {
            super("Uncompressed project is larger than " + limit + " bytes");
        }
    }
}
