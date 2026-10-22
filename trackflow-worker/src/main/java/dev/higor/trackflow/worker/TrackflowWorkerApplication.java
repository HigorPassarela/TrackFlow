package dev.higor.trackflow.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Consumes jobs from RabbitMQ, runs ffmpeg and reads Ableton projects, and publishes the results.
 * Runs apart from the API so that CPU-heavy work never competes with requests (design D1).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class TrackflowWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackflowWorkerApplication.class, args);
    }
}
