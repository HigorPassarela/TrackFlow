package dev.higor.trackflow.worker.audio;

import java.nio.file.Path;

public interface LoudnessMeter {

    Loudness measure(Path source);

    /** EBU R 128 integrated loudness and true peak, both rounded to one decimal place. */
    record Loudness(double integratedLufs, double truePeakDbtp) {}
}
