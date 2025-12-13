package com.netflix.spinnaker.orca;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.q.pending.PendingExecutionService;
import com.netflix.spinnaker.orca.webhook.pipeline.WebhookStage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = Main.class)
@TestPropertySource(
    properties = {
      "spring.config.location=classpath:orca-test.yml",
      "keiko.queue.redis.enabled = false"
    })
class HttpStatusCodeSpringObjectMapperTest {

  @Autowired ObjectMapper objectMapper;

  @MockBean ExecutionRepository executionRepository;

  @MockBean PendingExecutionService pendingExecutionService;

  @MockBean NotificationClusterLock notificationClusterLock;

  @Test
  void shouldFailToDeserializeStringStatusCode() {
    String json = """
      {
        "statusCode": "OK"
      }
      """;

    assertThrows(
        InvalidFormatException.class,
        () -> objectMapper.readValue(json, WebhookStage.WebhookMonitorResponseStageData.class));
  }
}
