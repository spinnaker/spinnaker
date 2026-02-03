/*
 * Copyright 2025 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.actuator.observability.model;

import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

/**
 * Verifies that sensitive API keys are not exposed when serializing config objects to JSON. This is
 * important for actuator endpoints that may expose configuration.
 */
public class ApiKeyMaskingTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void test_datadog_api_key_not_serialized() throws JsonProcessingException {
    MetricsDatadogConfig config =
        MetricsDatadogConfig.builder()
            .enabled(true)
            .apiKey("secret-datadog-api-key")
            .applicationKey("secret-application-key")
            .uri("https://api.datadoghq.com")
            .build();

    String json = objectMapper.writeValueAsString(config);

    assertFalse("API key should not appear in JSON output", json.contains("secret-datadog-api-key"));
    assertFalse(
        "Application key should not appear in JSON output",
        json.contains("secret-application-key"));
    assertTrue("URI should appear in JSON output", json.contains("https://api.datadoghq.com"));
  }

  @Test
  public void test_newrelic_api_key_not_serialized() throws JsonProcessingException {
    MetricsNewRelicConfig config =
        MetricsNewRelicConfig.builder()
            .enabled(true)
            .apiKey("secret-newrelic-api-key")
            .uri("https://metric-api.newrelic.com")
            .build();

    String json = objectMapper.writeValueAsString(config);

    assertFalse(
        "API key should not appear in JSON output", json.contains("secret-newrelic-api-key"));
    assertTrue("URI should appear in JSON output", json.contains("https://metric-api.newrelic.com"));
  }

  @Test
  public void test_datadog_api_key_can_be_deserialized() throws JsonProcessingException {
    String json =
        "{\"enabled\":true,\"apiKey\":\"my-api-key\",\"uri\":\"https://api.datadoghq.com\"}";

    MetricsDatadogConfig config = objectMapper.readValue(json, MetricsDatadogConfig.class);

    assertEquals("my-api-key", config.getApiKey());
    assertTrue(config.isEnabled());
  }

  @Test
  public void test_newrelic_api_key_can_be_deserialized() throws JsonProcessingException {
    String json =
        "{\"enabled\":true,\"apiKey\":\"my-newrelic-key\",\"uri\":\"https://metric-api.newrelic.com\"}";

    MetricsNewRelicConfig config = objectMapper.readValue(json, MetricsNewRelicConfig.class);

    assertEquals("my-newrelic-key", config.getApiKey());
    assertTrue(config.isEnabled());
  }
}
