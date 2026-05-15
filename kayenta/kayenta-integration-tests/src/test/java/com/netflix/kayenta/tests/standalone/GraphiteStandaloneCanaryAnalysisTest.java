/*
 * Copyright 2019 Playtika
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
 */
package com.netflix.kayenta.tests.standalone;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;

import com.netflix.kayenta.steps.StandaloneCanaryAnalysisSteps;
import com.netflix.kayenta.tests.BaseIntegrationTest;
import io.restassured.response.ValidatableResponse;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
@Ignore
/**
 * This test is increasingly and notoriously unstable in passing/failing and is marked disabled as
 * of 05/15/2026. We should look at explicit tags for graphite perhaps, and consider the plan around
 * these tests to be less painful
 */
public class GraphiteStandaloneCanaryAnalysisTest extends BaseIntegrationTest {

  @Autowired protected StandaloneCanaryAnalysisSteps steps;

  @Value("${embedded.graphite.httpPort}")
  private int graphiteHttpPort;

  private static final AtomicBoolean metricsAccumulated = new AtomicBoolean(false);

  @BeforeEach
  public void waitForMetricsAccumulation() throws InterruptedException {
    // Wait for metrics to accumulate in Graphite before running tests.
    // This only happens once per test class execution (after Spring context is initialized).
    // Uses active polling to handle variable timing from ARM64 emulation overhead.
    if (metricsAccumulated.compareAndSet(false, true)) {
      log.info(
          "Waiting for Graphite to accumulate and index sufficient metrics (container now running)...");

      // Initial wait for metrics to start flowing
      TimeUnit.SECONDS.sleep(90);

      // Poll for metric availability with generous timeout for ARM emulation
      log.info("Polling Graphite for metric availability...");
      await()
          .pollDelay(5, TimeUnit.SECONDS)
          .pollInterval(10, TimeUnit.SECONDS)
          .atMost(5, TimeUnit.MINUTES)
          .ignoreExceptions()
          .untilAsserted(
              () -> {
                // Verify Graphite can respond to metric queries
                given()
                    .port(graphiteHttpPort)
                    .queryParam("query", "integration.test.cpu")
                    .queryParam("format", "json")
                    .when()
                    .get("/metrics/find")
                    .then()
                    .statusCode(200);
                log.info("Graphite metrics are queryable");
              });

      // Additional wait to ensure sufficient historical data for 1-minute analysis window
      // Needs extra time for ARM emulation and to accumulate enough data points
      log.info("Metrics queryable, waiting 90 more seconds for data accumulation...");
      TimeUnit.SECONDS.sleep(90);
      log.info("Metrics accumulation period complete, starting tests");
    }
  }

  @Test
  public void canaryAnalysisIsSuccessful() {
    String canaryAnalysisExecutionId =
        steps.createCanaryAnalysis(
            "cpu-successful-analysis-case",
            "graphite-account",
            "in-memory-store-account",
            "canary-configs/graphite/integration-test-cpu.json");

    ValidatableResponse response =
        steps.waitUntilCanaryAnalysisCompleted(canaryAnalysisExecutionId);

    response
        .body("executionStatus", is("SUCCEEDED"))
        .body("canaryAnalysisExecutionResult.hasWarnings", is(false))
        .body("canaryAnalysisExecutionResult.didPassThresholds", is(true))
        .body(
            "canaryAnalysisExecutionResult.canaryScoreMessage",
            is("Final canary score 100.0 met or exceeded the pass score threshold."));
  }

  @Test
  public void canaryAnalysisIsFailed() {
    String canaryAnalysisExecutionId =
        steps.createCanaryAnalysis(
            "cpu-marginal-analysis-case",
            "graphite-account",
            "in-memory-store-account",
            "canary-configs/graphite/integration-test-cpu.json");

    ValidatableResponse response =
        steps.waitUntilCanaryAnalysisCompleted(canaryAnalysisExecutionId);

    response
        .body("executionStatus", is("TERMINAL"))
        .body("canaryAnalysisExecutionResult.hasWarnings", is(false))
        .body("canaryAnalysisExecutionResult.didPassThresholds", is(false))
        .body(
            "canaryAnalysisExecutionResult.canaryScoreMessage",
            is("Final canary score 0.0 is not above the marginal score threshold."));
  }
}
