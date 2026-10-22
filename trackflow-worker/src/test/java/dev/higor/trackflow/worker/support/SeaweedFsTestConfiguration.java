package dev.higor.trackflow.worker.support;

import java.io.IOException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

/**
 * Real S3-compatible storage for integration tests. Spring Boot has no service connection for
 * SeaweedFS, so the properties are registered by hand. The S3 identity is enabled on purpose:
 * without it SeaweedFS accepts unsigned requests, and signature bugs would go unnoticed.
 *
 * <p>The image tag must match {@code docker/compose.yaml}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class SeaweedFsTestConfiguration {

    public static final String BUCKET = "trackflow";
    public static final String ACCESS_KEY = "it-access-key";
    public static final String SECRET_KEY = "it-secret-key";
    private static final int S3_PORT = 8333;

    @Bean
    GenericContainer<?> seaweedfs() throws IOException, InterruptedException {
        String identities = """
                {"identities":[{"name":"it","credentials":[{"accessKey":"%s","secretKey":"%s"}],
                "actions":["Admin","Read","List","Tagging","Write"]}]}
                """.formatted(ACCESS_KEY, SECRET_KEY);
        GenericContainer<?> container = new GenericContainer<>("chrislusf/seaweedfs:4.48")
                .withCopyToContainer(Transferable.of(identities), "/etc/seaweedfs/s3.json")
                .withCommand("server", "-dir=/data", "-s3", "-s3.port=" + S3_PORT, "-s3.config=/etc/seaweedfs/s3.json")
                .withExposedPorts(S3_PORT)
                .waitingFor(Wait.forHttp("/").forPort(S3_PORT).forStatusCodeMatching(status -> status < 500));
        container.start();
        container.execInContainer("sh", "-c", "echo 's3.bucket.create -name " + BUCKET + "' | weed shell");
        return container;
    }

    @Bean
    DynamicPropertyRegistrar storageProperties(GenericContainer<?> seaweedfs) {
        return registry -> {
            registry.add(
                    "trackflow.storage.endpoint",
                    () -> "http://" + seaweedfs.getHost() + ":" + seaweedfs.getMappedPort(S3_PORT));
            registry.add("trackflow.storage.bucket", () -> BUCKET);
            registry.add("trackflow.storage.access-key", () -> ACCESS_KEY);
            registry.add("trackflow.storage.secret-key", () -> SECRET_KEY);
        };
    }
}
