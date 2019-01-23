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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.restassured.http.Header;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.netflix.kayenta.signalfx.EndToEndCanaryIntegrationTests.CANARY_WINDOW_IN_MINUTES;
import static io.restassured.RestAssured.given;

/**
 * Spring Test Config for the SignalFx Integration Tests.
 * Creates NUMBER_OF_INSTANCES_PER_MOCK_CLUSTER number of threads for a mock service with three clusters (control, healthy experiment and a unhealthy experiment)
 * These mock clusters will report metrics in their background threads to SignalFx for the IntegrationTest suite to integrate with.
 * <p>
 * The metrics that get sent from this config, should align with what is defined in integration-test-canary-config.json in the integration source set resources dir.
 */
@TestConfiguration
@Slf4j
public class SignalFxMockServiceReportingConfig {

  private static final int NUMBER_OF_INSTANCES_PER_MOCK_CLUSTER = 3;
  private static final String INGEST_ENDPOINT = "https://ingest.signalfx.com/v2/datapoint";
  private static final int MOCK_SERVICE_REPORTING_INTERVAL_IN_MILLISECONDS = 1000;

  public static final String SIGNAL_FX_SCOPE_IDENTIFYING_DIMENSION_NAME = "canary-scope";
  public static final String SIGNAL_FX_LOCATION_IDENTIFYING_DIMENSION_NAME = "location";
  public static final String KAYENTA_INTEGRATION_TEST_CPU_AVG_METRIC_NAME = "kayenta.integration-test.cpu.avg";
  public static final String KAYENTA_INTEGRATION_TEST_REQUEST_COUNT_METRIC_NAME = "kayenta.integration-test.request.count";
  public static final String CONTROL_SCOPE_NAME = "control";
  public static final String HEALTHY_EXPERIMENT_SCOPE_NAME = "healthy-experiment";
  public static final String UNHEALTHY_EXPERIMENT_SCOPE_NAME = "unhealthy-experiment";

  private final ExecutorService executorService;
  private final String signalFxApiToken;

  private String testId;
  private Instant metricsReportingStartTime;

  public SignalFxMockServiceReportingConfig(@Value("${kayenta.signalfx.apiKey}") final String signalFxApiToken) {
    executorService = Executors.newFixedThreadPool(9);
    this.signalFxApiToken = signalFxApiToken;
  }

  @Bean
  public String testId() {
    return testId;
  }

  @Bean
  public Instant metricsReportingStartTime() {
    return metricsReportingStartTime;
  }

  @PostConstruct
  public void start() {
    testId = UUID.randomUUID().toString();

    ImmutableList.of(CONTROL_SCOPE_NAME, HEALTHY_EXPERIMENT_SCOPE_NAME).forEach(scope -> {
      for (int i = 0; i < NUMBER_OF_INSTANCES_PER_MOCK_CLUSTER; i++) {
        executorService.submit(createMetricReportingMockService(scope, ImmutableMap.of(
            KAYENTA_INTEGRATION_TEST_CPU_AVG_METRIC_NAME, new Metric(10),
            KAYENTA_INTEGRATION_TEST_REQUEST_COUNT_METRIC_NAME, new Metric(0,
                ImmutableMap.of(
                    "uri", "/v1/some-endpoint",
                    "status_code", "400"
                )
            )
        ), UUID.randomUUID().toString()));
      }
    });

    ImmutableList.of(UNHEALTHY_EXPERIMENT_SCOPE_NAME).forEach(scope -> {
      for (int i = 0; i < NUMBER_OF_INSTANCES_PER_MOCK_CLUSTER; i++) {
        executorService.submit(createMetricReportingMockService(scope, ImmutableMap.of(
            KAYENTA_INTEGRATION_TEST_CPU_AVG_METRIC_NAME, new Metric(12),
            KAYENTA_INTEGRATION_TEST_REQUEST_COUNT_METRIC_NAME, new Metric(50,
                ImmutableMap.of(
                    "uri", "/v1/some-endpoint",
                    "status_code", "400"
                )
            )
        ), UUID.randomUUID().toString()));
      }
    });

    metricsReportingStartTime = Instant.now();

    if (Boolean.valueOf(System.getProperty("block.for.metrics", "true"))) {
      // Wait for the mock services to send data, before allowing the tests to run
      try {
        long pause = TimeUnit.MINUTES.toMillis(CANARY_WINDOW_IN_MINUTES) + TimeUnit.SECONDS.toMillis(15);
        log.info("Waiting for {} milliseconds for mock data to flow through SignalFx, before letting the integration tests run", pause);
        Thread.sleep(pause);
      } catch (InterruptedException e) {
        log.error("Failed to wait to send metrics", e);
        throw new RuntimeException(e);
      }
    }
  }

  @PreDestroy
  public void stop() {
    executorService.shutdownNow();
  }

  private Runnable createMetricReportingMockService(String scopeName, Map<String, Metric> metrics, String uuid) {
    return () -> {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          List<Map<String, Object>> signalfxMetrics = new LinkedList<>();
          metrics.forEach((metricName, metric) -> signalfxMetrics.add(
              ImmutableMap.of(
                  "metric", metricName,
                  "dimensions", ImmutableMap.builder()
                      .putAll(metric.getDimensions())
                      .put(SIGNAL_FX_SCOPE_IDENTIFYING_DIMENSION_NAME, scopeName)
                      .put(SIGNAL_FX_LOCATION_IDENTIFYING_DIMENSION_NAME, "us-west-2")
                      .put("env", "integration")
                      .put("test-id", testId)
                      .put("uuid", uuid)
                      .build(),
                  "value", metric.getValue() + new Random().nextInt(6)
              )
          ));
          Map<String, List<Map<String, Object>>> signalfxRequest = ImmutableMap.of(
              "gauge", signalfxMetrics
          );

          given()
              .header(new Header("X-SF-TOKEN", signalFxApiToken))
              .contentType("application/json")
              .body(signalfxRequest)
              .when()
              .post(INGEST_ENDPOINT)
              .then()
              .statusCode(200);

          try {
            Thread.sleep(MOCK_SERVICE_REPORTING_INTERVAL_IN_MILLISECONDS);
          } catch (InterruptedException e) {
            log.debug("Thread interrupted", e);
          }
        } catch (Throwable t) {
          log.error("FAILED TO REPORT METRICS TO SIGNALFX, SHUTTING DOWN JVM", t);
          System.exit(1);
        }
      }
    };
  }

  @Data
  private class Metric {

    public Metric(Integer value, Map<String, String> dimensions) {
      this.dimensions = dimensions;
      this.value = value;
    }

    public Metric(Integer value) {
      this.value = value;
      this.dimensions = new HashMap<>();
    }

    private Map<String, String> dimensions;
    private Integer value;
  }
}
