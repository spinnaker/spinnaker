/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.echo.microsoftteams;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MicrosoftTeamsTemplateEngineTest {

  private MicrosoftTeamsTemplateEngine templateEngine;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    templateEngine = new MicrosoftTeamsTemplateEngine(objectMapper, null);
  }

  @Test
  void testRenderEventNotificationTemplate() throws Exception {
    Map<String, Object> context = new HashMap<>();
    context.put("correlationId", UUID.randomUUID().toString());
    context.put("summary", "Test notification summary");
    context.put("message", "Test message");
    context.put("executionUrl", "http://localhost:9000/#/applications/test/executions/details/123");
    context.put("themeColor", "0076D7");
    context.put("spinnakerUrl", "http://localhost:9000");

    String result = templateEngine.render("event-notification", context);

    assertNotNull(result);
    JsonNode jsonNode = objectMapper.readTree(result);

    // Verify it's an Adaptive Card message
    assertEquals("message", jsonNode.get("type").asText());
    assertTrue(jsonNode.has("attachments"));

    JsonNode attachment = jsonNode.get("attachments").get(0);
    assertEquals("application/vnd.microsoft.card.adaptive", attachment.get("contentType").asText());

    JsonNode content = attachment.get("content");
    assertEquals("AdaptiveCard", content.get("type").asText());
    assertEquals("1.4", content.get("version").asText());
    assertTrue(content.has("body"));
    assertTrue(content.has("actions"));
  }

  @Test
  void testRenderPipelineNotificationTemplate() throws Exception {
    Map<String, Object> context = new HashMap<>();
    context.put("correlationId", UUID.randomUUID().toString());
    context.put("summary", "Pipeline test's deploy pipeline has completed successfully");
    context.put("cardTitle", "Pipeline Complete for TEST");
    context.put("themeColor", "73DB69");
    context.put("applicationName", "test");
    context.put("executionName", "deploy");
    context.put("executionUrl", "http://localhost:9000/#/applications/test/executions/details/123");
    context.put("status", "Complete");
    context.put("spinnakerUrl", "http://localhost:9000");

    String result = templateEngine.render("pipeline-notification", context);

    assertNotNull(result);
    JsonNode jsonNode = objectMapper.readTree(result);

    // Verify it's an Adaptive Card message
    assertEquals("message", jsonNode.get("type").asText());
    assertTrue(jsonNode.has("attachments"));

    JsonNode attachment = jsonNode.get("attachments").get(0);
    assertEquals("application/vnd.microsoft.card.adaptive", attachment.get("contentType").asText());

    JsonNode content = attachment.get("content");
    assertEquals("AdaptiveCard", content.get("type").asText());
    assertTrue(content.has("body"));
    assertTrue(content.has("actions"));
  }

  @Test
  void testRenderPipelineNotificationWithOptionalFields() throws Exception {
    Map<String, Object> context = new HashMap<>();
    context.put("correlationId", UUID.randomUUID().toString());
    context.put("summary", "Stage deploy for test's deploy pipeline has completed successfully");
    context.put("cardTitle", "Stage Complete for TEST");
    context.put("themeColor", "73DB69");
    context.put("applicationName", "test");
    context.put("executionName", "deploy");
    context.put("executionUrl", "http://localhost:9000/#/applications/test/executions/details/123");
    context.put("eventName", "Deploy to Production");
    context.put("eventNameLabel", "Stage Name");
    context.put("description", "Deploy application to production");
    context.put("customMessage", "Custom notification message");
    context.put("status", "Complete");
    context.put("spinnakerUrl", "http://localhost:9000");

    String result = templateEngine.render("pipeline-notification", context);

    assertNotNull(result);
    JsonNode jsonNode = objectMapper.readTree(result);

    // Verify it's an Adaptive Card with body content
    JsonNode content = jsonNode.get("attachments").get(0).get("content");
    assertTrue(content.has("body"));

    // Check that the rendered content includes our custom values
    String resultStr = result.toLowerCase();
    assertTrue(resultStr.contains("custom notification message"));
    assertTrue(resultStr.contains("deploy to production"));
  }

  @Test
  void testRenderWithInvalidTemplate() {
    Map<String, Object> context = new HashMap<>();

    assertThrows(
        MicrosoftTeamsTemplateEngine.TemplateRenderException.class,
        () -> templateEngine.render("non-existent-template", context));
  }

  @Test
  void testRenderProducesValidJson() throws Exception {
    Map<String, Object> context = new HashMap<>();
    context.put("correlationId", UUID.randomUUID().toString());
    context.put("summary", "Test");
    context.put("themeColor", "0076D7");
    context.put("executionUrl", "http://localhost:9000");

    String result = templateEngine.render("event-notification", context);

    // Should not throw exception - validates JSON
    assertDoesNotThrow(() -> objectMapper.readTree(result));
  }

  @Test
  void testCreateBaseContext() {
    Map<String, Object> context = templateEngine.createBaseContext();
    assertNotNull(context);
    assertTrue(context instanceof HashMap);
  }
}
