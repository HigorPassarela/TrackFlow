package dev.higor.trackflow.worker.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Full worker context against RabbitMQ and SeaweedFS from Testcontainers. Every {@code *IT} that
 * needs the whole worker uses this annotation, so they share one cached context.
 *
 * <p>Retries wait milliseconds instead of seconds: the tests check how many attempts happen, not
 * how long the production backoff is.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
        properties = {
            "spring.rabbitmq.listener.simple.retry.initial-interval=50ms",
            "spring.rabbitmq.listener.simple.retry.max-interval=200ms"
        })
@Import({SeaweedFsTestConfiguration.class, RabbitTestConfiguration.class})
public @interface IntegrationTest {}
