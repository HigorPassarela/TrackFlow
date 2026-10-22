package dev.higor.trackflow.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.higor.trackflow.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTest
@AutoConfigureMockMvc
class TrackflowApiApplicationIT {

    @Autowired
    MockMvcTester mvc;

    @Test
    void reportsHealthUp() {
        var response = mvc.get().uri("/actuator/health").exchange();

        assertThat(response).hasStatusOk();
        assertThat(response).bodyJson().extractingPath("$.status").isEqualTo("UP");
    }

    @Test
    void servesOpenApiDocument() {
        var response = mvc.get().uri("/v3/api-docs").exchange();

        assertThat(response).hasStatusOk();
        assertThat(response).bodyJson().extractingPath("$.openapi").asString().startsWith("3.");
    }
}
