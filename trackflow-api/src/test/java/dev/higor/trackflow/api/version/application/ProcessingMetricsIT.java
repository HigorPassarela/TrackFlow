package dev.higor.trackflow.api.version.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import dev.higor.trackflow.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The meters are visible where operators look for them. */
@IntegrationTest
@AutoConfigureMockMvc
class ProcessingMetricsIT {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void exposesProcessingMetricsOnTheActuator() {
        var names = mvc.get().uri("/actuator/metrics").with(oauth2Login()).exchange();
        var duration = mvc.get()
                .uri("/actuator/metrics/{name}", ProcessingMetrics.DURATION)
                .with(oauth2Login())
                .exchange();

        assertThat(names).hasStatusOk();
        assertThat(names).bodyJson().extractingPath("$.names").asArray().contains(ProcessingMetrics.DURATION);
        assertThat(duration).hasStatusOk();
    }
}
