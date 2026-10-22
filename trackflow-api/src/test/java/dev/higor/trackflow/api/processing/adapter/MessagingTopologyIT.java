package dev.higor.trackflow.api.processing.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import dev.higor.trackflow.api.support.IntegrationTest;
import dev.higor.trackflow.contracts.Topology;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Reads the declared queues straight from the broker, the way an operator would. */
@IntegrationTest
class MessagingTopologyIT {

    @Autowired
    private RabbitMQContainer rabbitmq;

    @Test
    void declaresApiQueuesWithDeadLettering() throws Exception {
        Map<String, Map<String, Object>> queues = queues();

        for (String queue : List.of(Topology.API_AUDIO_PROCESSED_QUEUE, Topology.API_ABLETON_ANALYZED_QUEUE)) {
            assertThat(queues).containsKey(queue);
            assertThat(queues.get(queue))
                    .containsEntry("x-dead-letter-exchange", Topology.DEAD_LETTER_EXCHANGE)
                    .containsEntry("x-dead-letter-routing-key", queue);
            assertThat(queues).containsKey(Topology.deadLetterQueueOf(queue));
        }
    }

    /** Queue name to its arguments, as {@code rabbitmqctl} reports them. */
    private Map<String, Map<String, Object>> queues() throws Exception {
        var result = rabbitmq.execInContainer(
                "rabbitmqctl", "list_queues", "name", "arguments", "--formatter", "json", "--quiet");
        List<Map<String, Object>> rows =
                JsonMapper.builder().build().readValue(result.getStdout(), new TypeReference<>() {});
        return rows.stream()
                .collect(Collectors.toMap(row -> (String) row.get("name"), row -> argumentsOf(row.get("arguments"))));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> argumentsOf(Object arguments) {
        return arguments instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
