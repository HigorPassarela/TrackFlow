package dev.higor.trackflow.contracts;

/**
 * Names of the RabbitMQ topology shared by the API and the worker (design D6). Each app declares
 * the queues it consumes; both use these constants so a typo cannot silently route messages
 * nowhere.
 *
 * <p>Every queue dead-letters to {@link #DEAD_LETTER_EXCHANGE} with its own name as routing key,
 * landing in {@code <queue>.dlq} for manual inspection.
 */
public final class Topology {

    public static final String JOBS_EXCHANGE = "trackflow.jobs";
    public static final String RESULTS_EXCHANGE = "trackflow.results";
    public static final String DEAD_LETTER_EXCHANGE = "trackflow.dlx";

    public static final String AUDIO_PROCESS_ROUTING_KEY = "audio.process";
    public static final String ABLETON_ANALYZE_ROUTING_KEY = "ableton.analyze";
    public static final String AUDIO_PROCESSED_ROUTING_KEY = "audio.processed";
    public static final String ABLETON_ANALYZED_ROUTING_KEY = "ableton.analyzed";

    public static final String WORKER_AUDIO_PROCESS_QUEUE = "worker.audio-process";
    public static final String WORKER_ABLETON_ANALYZE_QUEUE = "worker.ableton-analyze";
    public static final String API_AUDIO_PROCESSED_QUEUE = "api.audio-processed";
    public static final String API_ABLETON_ANALYZED_QUEUE = "api.ableton-analyzed";

    private static final String DEAD_LETTER_SUFFIX = ".dlq";

    private Topology() {}

    /** {@code worker.audio-process} → {@code worker.audio-process.dlq}. */
    public static String deadLetterQueueOf(String queue) {
        return queue + DEAD_LETTER_SUFFIX;
    }
}
