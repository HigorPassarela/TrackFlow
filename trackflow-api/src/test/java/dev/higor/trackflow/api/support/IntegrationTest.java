package dev.higor.trackflow.api.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Full application context against real infrastructure from Testcontainers. Every {@code *IT} that
 * needs the whole application uses this annotation, so they all share one cached context.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@Import({PostgresTestConfiguration.class, SeaweedFsTestConfiguration.class, RabbitTestConfiguration.class})
public @interface IntegrationTest {}
