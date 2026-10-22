package dev.higor.trackflow.worker.messaging;

import static dev.higor.trackflow.contracts.Topology.ABLETON_ANALYZE_ROUTING_KEY;
import static dev.higor.trackflow.contracts.Topology.AUDIO_PROCESS_ROUTING_KEY;
import static dev.higor.trackflow.contracts.Topology.DEAD_LETTER_EXCHANGE;
import static dev.higor.trackflow.contracts.Topology.JOBS_EXCHANGE;
import static dev.higor.trackflow.contracts.Topology.RESULTS_EXCHANGE;
import static dev.higor.trackflow.contracts.Topology.WORKER_ABLETON_ANALYZE_QUEUE;
import static dev.higor.trackflow.contracts.Topology.WORKER_AUDIO_PROCESS_QUEUE;
import static dev.higor.trackflow.contracts.Topology.deadLetterQueueOf;

import dev.higor.trackflow.worker.ableton.InvalidProjectException;
import dev.higor.trackflow.worker.audio.InvalidMediaException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The part of the D6 topology the worker owns: the queues it consumes, each with its dead-letter
 * queue, plus the exchanges it publishes to. The API declares its own queues the same way, so
 * whichever app starts first, the broker ends up with the same topology.
 */
@Configuration(proxyBeanMethods = false)
class MessagingConfiguration {

    /**
     * Always reads a message as the listener's declared type, never as the Java class named in the
     * {@code __TypeId__} header. For sealed results the subtype then comes from the logical {@code
     * outcome} property, so no class name from the wire decides what gets instantiated.
     */
    @Bean
    MessageConverter messageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }

    @Bean
    Declarables workerTopology() {
        DirectExchange jobs =
                ExchangeBuilder.directExchange(JOBS_EXCHANGE).durable(true).build();
        DirectExchange results =
                ExchangeBuilder.directExchange(RESULTS_EXCHANGE).durable(true).build();
        DirectExchange deadLetters = ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE)
                .durable(true)
                .build();
        Queue audio = consumedQueue(WORKER_AUDIO_PROCESS_QUEUE);
        Queue ableton = consumedQueue(WORKER_ABLETON_ANALYZE_QUEUE);
        Queue audioDlq = QueueBuilder.durable(deadLetterQueueOf(WORKER_AUDIO_PROCESS_QUEUE))
                .build();
        Queue abletonDlq = QueueBuilder.durable(deadLetterQueueOf(WORKER_ABLETON_ANALYZE_QUEUE))
                .build();
        return new Declarables(
                jobs,
                results,
                deadLetters,
                audio,
                ableton,
                audioDlq,
                abletonDlq,
                bind(audio, jobs, AUDIO_PROCESS_ROUTING_KEY),
                bind(ableton, jobs, ABLETON_ANALYZE_ROUTING_KEY),
                bind(audioDlq, deadLetters, WORKER_AUDIO_PROCESS_QUEUE),
                bind(abletonDlq, deadLetters, WORKER_ABLETON_ANALYZE_QUEUE));
    }

    /**
     * Retries come from {@code spring.rabbitmq.listener.simple.retry}; this only says what is never
     * retried. A bad file stays bad, so it fails at once instead of after three attempts.
     */
    @Bean
    RabbitListenerRetrySettingsCustomizer neverRetryInvalidFiles() {
        return settings -> settings.setExceptionPredicate(MessagingConfiguration::isTemporary);
    }

    static boolean isTemporary(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof InvalidMediaException || cause instanceof InvalidProjectException) {
                return false;
            }
        }
        return true;
    }

    /** Rejected messages go to the dead-letter exchange, keyed by the queue name. */
    private static Queue consumedQueue(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(name)
                .build();
    }

    private static Binding bind(Queue queue, DirectExchange exchange, String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }
}
