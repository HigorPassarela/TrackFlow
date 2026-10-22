package dev.higor.trackflow.worker.ableton;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.zip.GZIPOutputStream;

/**
 * Fixtures are kept as readable XML in {@code fixtures/ableton/} and gzip-compressed on the fly,
 * which is exactly what Live does when it saves a {@code .als}.
 */
public final class AbletonFixtures {

    private AbletonFixtures() {}

    /** The fixture as Live would store it. */
    public static byte[] als(String fixture) {
        try (InputStream xml = AbletonFixtures.class.getResourceAsStream("/fixtures/ableton/" + fixture)) {
            if (xml == null) {
                throw new IllegalArgumentException("No fixture " + fixture);
            }
            return gzip(xml.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] gzip(byte[] content) {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (OutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return compressed.toByteArray();
    }

    /**
     * A valid project start followed by whitespace: about 1 MB compressed, over 300 MB once
     * expanded. Whitespace keeps the XML well formed, so only the size limit can stop the reader.
     */
    public static byte[] decompressionBomb() {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (OutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write("<Ableton Creator=\"Ableton Live 12.4.6\"><LiveSet><Tracks>".getBytes());
            byte[] spaces = " ".repeat(1024 * 1024).getBytes();
            for (int i = 0; i < 301; i++) {
                gzip.write(spaces);
            }
            gzip.write("</Tracks></LiveSet></Ableton>".getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return compressed.toByteArray();
    }
}
