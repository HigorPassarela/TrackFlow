/**
 * The bridge to the worker: turns confirmed versions into jobs on RabbitMQ and worker results into
 * calls to the version and ableton modules. Delivery is at least once (Modulith event registry +
 * publisher confirms), and every consumer on both sides is idempotent.
 *
 * <p>No public API: nothing depends on this module.
 */
@ApplicationModule(displayName = "Processing")
package dev.higor.trackflow.api.processing;

import org.springframework.modulith.ApplicationModule;
