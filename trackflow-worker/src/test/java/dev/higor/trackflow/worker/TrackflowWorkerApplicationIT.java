package dev.higor.trackflow.worker;

import static org.assertj.core.api.Assertions.assertThat;

import dev.higor.trackflow.worker.storage.ObjectStorage;
import dev.higor.trackflow.worker.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.context.ApplicationContext;

@IntegrationTest
class TrackflowWorkerApplicationIT {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private RabbitListenerEndpointRegistry listeners;

    @Test
    void startsWithoutAWebServerAndListensToItsQueues() {
        assertThat(context.getEnvironment().getProperty("spring.main.web-application-type"))
                .isEqualTo("none");
        assertThat(context.getBean(ObjectStorage.class)).isNotNull();
        assertThat(listeners.getListenerContainers())
                .isNotEmpty()
                .allSatisfy(container -> assertThat(container.isRunning()).isTrue());
    }

    @Test
    void reportsHealthThroughActuator() {
        assertThat(context.getBeanProvider(HealthEndpoint.class).getIfAvailable())
                .isNotNull()
                .satisfies(health ->
                        assertThat(health.health().getStatus().getCode()).isEqualTo("UP"));
    }
}
