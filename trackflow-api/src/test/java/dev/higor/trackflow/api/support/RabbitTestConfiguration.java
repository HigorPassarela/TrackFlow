package dev.higor.trackflow.api.support;

import static dev.higor.trackflow.contracts.Topology.ABLETON_ANALYZE_ROUTING_KEY;
import static dev.higor.trackflow.contracts.Topology.AUDIO_PROCESS_ROUTING_KEY;
import static dev.higor.trackflow.contracts.Topology.JOBS_EXCHANGE;
import static dev.higor.trackflow.contracts.Topology.WORKER_ABLETON_ANALYZE_QUEUE;
import static dev.higor.trackflow.contracts.Topology.WORKER_AUDIO_PROCESS_QUEUE;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * Real RabbitMQ for integration tests. API tests play the worker's side through messages, so this
 * also declares the worker's queues (without consumers): jobs have somewhere to land and tests can
 * read them back.
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
    Declarables simulatedWorkerQueues() {
        DirectExchange jobs = new DirectExchange(JOBS_EXCHANGE);
        Queue audio = new Queue(WORKER_AUDIO_PROCESS_QUEUE);
        Queue ableton = new Queue(WORKER_ABLETON_ANALYZE_QUEUE);
        return new Declarables(
                audio,
                ableton,
                BindingBuilder.bind(audio).to(jobs).with(AUDIO_PROCESS_ROUTING_KEY),
                BindingBuilder.bind(ableton).to(jobs).with(ABLETON_ANALYZE_ROUTING_KEY));
    }
}
