package dev.higor.trackflow.contracts;

/**
 * Names of the RabbitMQ topology shared by the API and the worker. Each app declares the queues it
 * consumes; both use these constants so a typo cannot silently route messages nowhere.
 */
public final class Topology {

    public static final String JOBS_EXCHANGE = "trackflow.jobs";
    public static final String RESULTS_EXCHANGE = "trackflow.results";
    public static final String DEAD_LETTER_EXCHANGE = "trackflow.dlx";

    public static final String AUDIO_PROCESS_ROUTING_KEY = "audio.process";
    public static final String AUDIO_PROCESSED_ROUTING_KEY = "audio.processed";

    public static final String WORKER_AUDIO_PROCESS_QUEUE = "worker.audio-process";
    public static final String API_AUDIO_PROCESSED_QUEUE = "api.audio-processed";

    private Topology() {}
}
