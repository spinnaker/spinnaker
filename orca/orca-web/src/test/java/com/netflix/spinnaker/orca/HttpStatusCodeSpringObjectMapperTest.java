/*
 * Copyright 2025 OpsMx, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.q.pending.PendingExecutionService;
import com.netflix.spinnaker.orca.webhook.pipeline.WebhookStage;
import org.assertj.core.api.Assertions;
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
  void shouldFailToDeserializeInvalidStringStatusCode() {
    String json = """
      {
        "statusCode": "InvalidStatusCode"
      }
      """;

    assertThrows(
        InvalidFormatException.class,
        () -> objectMapper.readValue(json, WebhookStage.WebhookMonitorResponseStageData.class));
  }

  @Test
  void shouldFailToDeserializeInvalidIntStatusCode() {
    String json = """
      {
        "statusCode": 20
      }
      """;

    assertThrows(
        JsonMappingException.class,
        () -> objectMapper.readValue(json, WebhookStage.WebhookMonitorResponseStageData.class));
  }

  @Test
  void shouldDeserializeStringStatusCode() throws JsonProcessingException {
    String json = """
      {
        "statusCode": "OK"
      }
      """;

    WebhookStage.WebhookMonitorResponseStageData monitor =
        objectMapper.readValue(json, WebhookStage.WebhookMonitorResponseStageData.class);
    Assertions.assertThat(monitor.getStatusCode().is2xxSuccessful()).isTrue();
  }

  @Test
  void shouldDeserializeIntStatusCode() throws JsonProcessingException {
    String json = """
      {
        "statusCode": 200
      }
      """;

    WebhookStage.WebhookMonitorResponseStageData monitor =
        objectMapper.readValue(json, WebhookStage.WebhookMonitorResponseStageData.class);
    Assertions.assertThat(monitor.getStatusCode().is2xxSuccessful()).isTrue();
  }
}
