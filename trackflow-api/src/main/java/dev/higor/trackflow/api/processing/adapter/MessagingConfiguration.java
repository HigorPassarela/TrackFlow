package dev.higor.trackflow.api.processing.adapter;

import static dev.higor.trackflow.contracts.Topology.ABLETON_ANALYZED_ROUTING_KEY;
import static dev.higor.trackflow.contracts.Topology.API_ABLETON_ANALYZED_QUEUE;
import static dev.higor.trackflow.contracts.Topology.API_AUDIO_PROCESSED_QUEUE;
import static dev.higor.trackflow.contracts.Topology.AUDIO_PROCESSED_ROUTING_KEY;
import static dev.higor.trackflow.contracts.Topology.DEAD_LETTER_EXCHANGE;
import static dev.higor.trackflow.contracts.Topology.JOBS_EXCHANGE;
import static dev.higor.trackflow.contracts.Topology.RESULTS_EXCHANGE;
import static dev.higor.trackflow.contracts.Topology.deadLetterQueueOf;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The part of the D6 topology the API owns: the exchanges it publishes to and the queues it
 * consumes, each with its dead-letter queue. The worker declares its own queues the same way.
 */
@Configuration(proxyBeanMethods = false)
class MessagingConfiguration {

    @Bean
    MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    Declarables apiTopology() {
        DirectExchange jobs =
                ExchangeBuilder.directExchange(JOBS_EXCHANGE).durable(true).build();
        DirectExchange results =
                ExchangeBuilder.directExchange(RESULTS_EXCHANGE).durable(true).build();
        DirectExchange deadLetters = ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE)
                .durable(true)
                .build();
        Queue audioProcessed = consumedQueue(API_AUDIO_PROCESSED_QUEUE);
        Queue abletonAnalyzed = consumedQueue(API_ABLETON_ANALYZED_QUEUE);
        Queue audioProcessedDlq = QueueBuilder.durable(deadLetterQueueOf(API_AUDIO_PROCESSED_QUEUE))
                .build();
        Queue abletonAnalyzedDlq = QueueBuilder.durable(deadLetterQueueOf(API_ABLETON_ANALYZED_QUEUE))
                .build();
        return new Declarables(
                jobs,
                results,
                deadLetters,
                audioProcessed,
                abletonAnalyzed,
                audioProcessedDlq,
                abletonAnalyzedDlq,
                bind(audioProcessed, results, AUDIO_PROCESSED_ROUTING_KEY),
                bind(abletonAnalyzed, results, ABLETON_ANALYZED_ROUTING_KEY),
                bind(audioProcessedDlq, deadLetters, API_AUDIO_PROCESSED_QUEUE),
                bind(abletonAnalyzedDlq, deadLetters, API_ABLETON_ANALYZED_QUEUE));
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
