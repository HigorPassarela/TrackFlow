package dev.higor.trackflow.api.processing.application;

import dev.higor.trackflow.contracts.ProcessAudioJob;

/**
 * Port for sending work to the worker. The application layer does not know RabbitMQ exists.
 *
 * <p>Implementations must throw if the broker did not confirm the message: the caller relies on the
 * exception to keep the triggering event incomplete and retry it later.
 */
public interface JobPublisher {

    void publish(ProcessAudioJob job);
}
