package dev.higor.trackflow.worker.messaging;

import dev.higor.trackflow.contracts.AbletonAnalyzed;
import dev.higor.trackflow.contracts.AudioProcessed;
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
 * Sends results to the API. A result only counts as sent when the broker confirmed it and routed
 * it to a queue; otherwise the exception makes the listener retry the whole job, which is safe
 * because outputs have deterministic keys and the API ignores duplicated results.
 */
@Component
class ResultPublisher {

    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);

    private final RabbitTemplate rabbit;

    ResultPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    void publish(AudioProcessed result) {
        send(Topology.AUDIO_PROCESSED_ROUTING_KEY, result, result.messageId());
    }

    void publish(AbletonAnalyzed result) {
        send(Topology.ABLETON_ANALYZED_ROUTING_KEY, result, result.messageId());
    }

    private void send(String routingKey, Object result, UUID messageId) {
        CorrelationData correlation = new CorrelationData(messageId.toString());
        rabbit.convertAndSend(
                Topology.RESULTS_EXCHANGE,
                routingKey,
                result,
                message -> {
                    message.getMessageProperties().setMessageId(messageId.toString());
                    return message;
                },
                correlation);
        CorrelationData.Confirm confirm = awaitConfirm(correlation, routingKey);
        if (!confirm.ack()) {
            throw new AmqpException("Broker refused result routingKey=" + routingKey + " cause=" + confirm.reason());
        }
        if (correlation.getReturned() != null) {
            throw new AmqpException("Result was not routed to any queue routingKey=" + routingKey);
        }
    }

    private static CorrelationData.Confirm awaitConfirm(CorrelationData correlation, String routingKey) {
        try {
            return correlation.getFuture().get(CONFIRM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted waiting for broker confirm routingKey=" + routingKey, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new AmqpException("No broker confirm for result routingKey=" + routingKey, e);
        }
    }
}
