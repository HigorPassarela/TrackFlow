package dev.higor.trackflow.worker.support;

import static dev.higor.trackflow.contracts.Topology.ABLETON_ANALYZED_ROUTING_KEY;
import static dev.higor.trackflow.contracts.Topology.API_ABLETON_ANALYZED_QUEUE;
import static dev.higor.trackflow.contracts.Topology.API_AUDIO_PROCESSED_QUEUE;
import static dev.higor.trackflow.contracts.Topology.AUDIO_PROCESSED_ROUTING_KEY;
import static dev.higor.trackflow.contracts.Topology.RESULTS_EXCHANGE;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * Real RabbitMQ for integration tests. Worker tests play the API's side through messages, so this
 * also declares the API's result queues (without consumers): results have somewhere to land and
 * tests can read them back.
 *
 * <p>The image tag must match {@code docker/compose.yaml}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RabbitTestConfiguration {

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitmq() {
        return new RabbitMQContainer("rabbitmq:4.4-management");
    }

    @Bean
    Declarables simulatedApiQueues() {
        DirectExchange results = new DirectExchange(RESULTS_EXCHANGE);
        Queue audio = new Queue(API_AUDIO_PROCESSED_QUEUE);
        Queue ableton = new Queue(API_ABLETON_ANALYZED_QUEUE);
        return new Declarables(
                audio,
                ableton,
                BindingBuilder.bind(audio).to(results).with(AUDIO_PROCESSED_ROUTING_KEY),
                BindingBuilder.bind(ableton).to(results).with(ABLETON_ANALYZED_ROUTING_KEY));
    }
}
