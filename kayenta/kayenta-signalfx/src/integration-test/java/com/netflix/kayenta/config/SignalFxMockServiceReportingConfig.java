/*
 * Copyright (c) 2018 Nike, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 *
 */

package com.netflix.kayenta.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Spring Test Config for the SignalFx integration tests.
 *
 * <p>Historically these tests spun up threads that pushed metrics into a real Splunk Observability
 * account (via {@code ingest.signalfx.com}) and then polled the live SignalFlow API. That required
 * a valid API key and a network round trip, which made the tests unusable in CI. This replacement
 * stands up an {@link MockWebServer} (managed statically by the base test class) that speaks the
 * same SignalFlow SSE wire protocol Kayenta queries, and serves canned data shaped to make each
 * canary judgment deterministic:
 *
 * <ul>
 *   <li>{@code control} and {@code healthy-experiment}: identical low, low-variance values → the
 *       judge classifies as {@code Pass}.
 *   <li>{@code unhealthy-experiment}: elevated values on the metric marked {@code critical} → the
 *       judge classifies as {@code Fail} with a "High" classification reason.
 *   <li>Any metric name containing {@code errors} (the two non-existent metrics in the canary
 *       config): empty data stream, mirroring "no data in SignalFx".
 * </ul>
 */
@TestConfiguration
@Slf4j
public class SignalFxMockServiceReportingConfig {

  public static final String CONTROL_SCOPE_NAME = "control";
  public static final String HEALTHY_EXPERIMENT_SCOPE_NAME = "healthy-experiment";
  public static final String UNHEALTHY_EXPERIMENT_SCOPE_NAME = "unhealthy-experiment";

  private static final int POINT_COUNT = 60;
  private static final long STEP_MS = 1000L;
  private static final String TS_ID = "AAAAAFOJhJg";

  private final String testId = UUID.randomUUID().toString();
  private final Instant metricsReportingStartTime =
      Instant.now().minusSeconds(POINT_COUNT * STEP_MS / 1000);

  @Bean
  public String testId() {
    return testId;
  }

  @Bean
  public Instant metricsReportingStartTime() {
    return metricsReportingStartTime;
  }

  /** Starts a MockWebServer that dispatches SignalFlow SSE responses. */
  public static MockWebServer startMockSignalFlowServer() throws IOException {
    MockWebServer server = new MockWebServer();
    server.setDispatcher(new SignalFlowDispatcher());
    server.start();
    log.info("Mock SignalFlow server listening at {}", server.url("/"));
    return server;
  }

  private static final class SignalFlowDispatcher extends Dispatcher {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @NotNull
    @Override
    public MockResponse dispatch(@NotNull RecordedRequest request) {
      String path = request.getPath() != null ? request.getPath() : "";
      if (!path.startsWith("/v2/signalflow/execute")) {
        return new MockResponse().setResponseCode(404);
      }
      String program = request.getBody().readUtf8();
      String scope = extractScope(program);
      double base = baselineFor(scope, program);
      boolean emitData = !program.contains("errors");
      String metric = program.contains("cpu.avg") ? "cpu" : "req";
      // Seed by metric only (not scope) so control and healthy-experiment produce identical
      // noise patterns — the canary judge should classify that as Pass. The unhealthy scope
      // shifts the baseline, which the judge should classify as Fail.
      long seed = metric.hashCode();
      return new MockResponse()
          .setResponseCode(200)
          .setHeader("Content-Type", "text/plain")
          .setBody(buildSseBody(base, emitData, seed));
    }

    private String extractScope(String program) {
      for (String candidate :
          new String[] {
            UNHEALTHY_EXPERIMENT_SCOPE_NAME, HEALTHY_EXPERIMENT_SCOPE_NAME, CONTROL_SCOPE_NAME
          }) {
        if (program.contains("'" + candidate + "'")) {
          return candidate;
        }
      }
      return CONTROL_SCOPE_NAME;
    }

    private double baselineFor(String scope, String program) {
      boolean cpuMetric = program.contains("cpu.avg");
      boolean requestMetric = program.contains("request.count");
      if (UNHEALTHY_EXPERIMENT_SCOPE_NAME.equals(scope)) {
        if (cpuMetric) return 60.0;
        if (requestMetric) return 50.0;
      }
      if (cpuMetric) return 10.0;
      if (requestMetric) return 0.0;
      return 0.0;
    }

    private String buildSseBody(double base, boolean emitData, long seed) {
      Random random = new Random(seed);
      StringBuilder sb = new StringBuilder();
      appendEvent(
          sb, "control-message", ImmutableMap.of("event", "STREAM_START", "timestampMs", 0));
      if (emitData) {
        long ts = 1_700_000_000_000L;
        for (int i = 0; i < POINT_COUNT; i++) {
          double value = base + random.nextInt(3);
          appendEvent(
              sb,
              "data",
              ImmutableMap.of(
                  "data",
                  ImmutableList.of(ImmutableMap.of("tsId", TS_ID, "value", value)),
                  "logicalTimestampMs",
                  ts + i * STEP_MS));
        }
      }
      appendEvent(
          sb, "control-message", ImmutableMap.of("event", "END_OF_CHANNEL", "timestampMs", 0));
      return sb.toString();
    }

    private void appendEvent(StringBuilder sb, String eventName, Map<String, ?> payload) {
      String json;
      try {
        json = objectMapper.writeValueAsString(payload);
      } catch (JsonProcessingException e) {
        throw new IllegalStateException("Failed to serialize mock SignalFlow payload", e);
      }
      sb.append("event: ").append(eventName).append('\n');
      sb.append("data: ").append(json).append("\n\n");
    }
  }
}
