package dev.higor.trackflow.api.processing.adapter;

import dev.higor.trackflow.api.processing.application.JobPublisher;
import dev.higor.trackflow.contracts.ProcessAudioJob;
import dev.higor.trackflow.contracts.Topology;
import java.time.Duration;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ adapter of {@link JobPublisher}. Requires {@code spring.rabbitmq.publisher-confirm-type:
 * simple}: the send only counts once the broker confirms it.
 */
@Component
class RabbitJobPublisher implements JobPublisher {

    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);

    private final RabbitTemplate rabbit;

    RabbitJobPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @Override
    public void publish(ProcessAudioJob job) {
        rabbit.invoke(operations -> {
            operations.convertAndSend(Topology.JOBS_EXCHANGE, Topology.AUDIO_PROCESS_ROUTING_KEY, job, message -> {
                message.getMessageProperties().setMessageId(job.messageId().toString());
                return message;
            });
            // Throws AmqpException when the broker nacks or does not answer in time.
            operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT.toMillis());
            return null;
        });
    }
}
