package dev.higor.trackflow.worker.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.higor.trackflow.worker.audio.PcmAnalyzer.PcmAnalysis;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("ffmpeg")
class FfmpegTranscoderTest {

    private final Transcoder transcoder = FfmpegTestSupport.transcoder();
    private final MediaProbe probe = FfmpegTestSupport.probe();

    @TempDir
    Path directory;

    @Test
    @DisplayName("Scenario: preview of a 6-minute WAV")
    void previewKeepsTheDurationAndStaysSmall() throws Exception {
        Path wav = TestSignals.writeWav(directory.resolve("bounce.wav"), TestSignals.metronome(124, 360));
        Path preview = directory.resolve("preview.mp3");

        transcoder.encodePreview(wav, preview);

        assertThat(probe.probe(preview).durationMs()).isCloseTo(360_000, within(50L));
        assertThat(Files.size(preview)).isLessThan(10L * 1024 * 1024);
    }

    @Test
    void decodesToMonoPcmAtTheAnalysisRate() {
        Path wav = TestSignals.writeWav(directory.resolve("bounce.wav"), TestSignals.sine(440, 0.5, 2));
        Path pcm = directory.resolve("audio.pcm");

        transcoder.decodeToPcm(wav, pcm);
        PcmAnalysis analysis = PcmAnalyzer.analyze(pcm, Transcoder.PCM_SAMPLE_RATE);

        assertThat(analysis.durationMs()).isEqualTo(2000);
        assertThat(analysis.peaks()).hasSize(2 * PeaksCalculator.POINTS_PER_SECOND);
    }

    @Test
    @DisplayName("Scenario: screen recording")
    void decodesTheAudioStreamOfAVideo() {
        Path wav = TestSignals.writeWav(directory.resolve("ableton.wav"), TestSignals.sine(440, 0.5, 3));
        Path video = FfmpegTestFiles.videoWithAudio(directory.resolve("screen.mp4"), wav);
        Path pcm = directory.resolve("audio.pcm");

        transcoder.decodeToPcm(video, pcm);

        assertThat(PcmAnalyzer.analyze(pcm, Transcoder.PCM_SAMPLE_RATE).durationMs())
                .isCloseTo(3000, within(100L));
    }

    @Test
    void reportsUndecodableAudioAsUnreadable() throws Exception {
        Path wav = TestSignals.writeWav(directory.resolve("bounce.wav"), TestSignals.sine(440, 0.5, 1));
        byte[] header = Files.readAllBytes(wav);
        // Keep the RIFF header but claim a codec ffmpeg does not have.
        header[20] = (byte) 0x7F;
        Files.write(wav, header);

        assertThatThrownBy(() -> transcoder.decodeToPcm(wav, directory.resolve("audio.pcm")))
                .isInstanceOf(InvalidMediaException.class);
    }
}
