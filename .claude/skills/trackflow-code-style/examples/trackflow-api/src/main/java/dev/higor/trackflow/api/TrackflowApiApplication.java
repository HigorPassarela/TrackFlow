package dev.higor.trackflow.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Each direct sub-package of this one is a Spring Modulith application module. Nothing else lives
 * at this level besides this class.
 */
@SpringBootApplication
@EnableScheduling
public class TrackflowApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackflowApiApplication.class, args);
    }
}
