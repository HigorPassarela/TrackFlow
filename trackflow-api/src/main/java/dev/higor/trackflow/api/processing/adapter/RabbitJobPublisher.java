package dev.higor.trackflow.api.processing.adapter;

import dev.higor.trackflow.api.processing.application.JobPublisher;
import dev.higor.trackflow.contracts.AnalyzeAbletonProjectJob;
import dev.higor.trackflow.contracts.ProcessAudioJob;
import dev.higor.trackflow.contracts.Topology;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ adapter of {@link JobPublisher}. A send only counts when the broker confirmed it
 * <em>and</em> did not return it as unroutable: a plain confirm also acks a message that reached an
 * exchange with no queue bound yet (worker not started), which would silently lose the job.
 *
 * <p>Requires {@code publisher-confirm-type: correlated}, {@code publisher-returns: true} and
 * {@code template.mandatory: true}.
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
        send(Topology.AUDIO_PROCESS_ROUTING_KEY, job, job.messageId());
    }

    @Override
    public void publish(AnalyzeAbletonProjectJob job) {
        send(Topology.ABLETON_ANALYZE_ROUTING_KEY, job, job.messageId());
    }

    private void send(String routingKey, Object job, UUID messageId) {
        CorrelationData correlation = new CorrelationData(messageId.toString());
        rabbit.convertAndSend(
                Topology.JOBS_EXCHANGE,
                routingKey,
                job,
                message -> {
                    message.getMessageProperties().setMessageId(messageId.toString());
                    return message;
                },
                correlation);
        CorrelationData.Confirm confirm = awaitConfirm(correlation, routingKey);
        if (!confirm.ack()) {
            throw new AmqpException("Broker refused job routingKey=" + routingKey + " cause=" + confirm.reason());
        }
        if (correlation.getReturned() != null) {
            throw new AmqpException("Job was not routed to any queue routingKey=" + routingKey);
        }
    }

    private static CorrelationData.Confirm awaitConfirm(CorrelationData correlation, String routingKey) {
        try {
            return correlation.getFuture().get(CONFIRM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted waiting for broker confirm routingKey=" + routingKey, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new AmqpException("No broker confirm for job routingKey=" + routingKey, e);
        }
    }
}
