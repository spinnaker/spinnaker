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

import static org.hamcrest.CoreMatchers.is;

import com.netflix.kayenta.steps.StandaloneCanaryAnalysisSteps;
import com.netflix.kayenta.tests.BaseIntegrationTest;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class PrometheusStandaloneCanaryAnalysisTest extends BaseIntegrationTest {

  @Autowired protected StandaloneCanaryAnalysisSteps steps;

  @Test
  public void canaryAnalysisIsSuccessful() {
    String canaryAnalysisExecutionId =
        steps.createCanaryAnalysis(
            "cpu-successful-analysis-case",
            "prometheus-account",
            "minio-store-account",
            "canary-configs/prometheus/integration-test-cpu.json");

    ValidatableResponse response =
        steps.waitUntilCanaryAnalysisCompleted(canaryAnalysisExecutionId);

    response
        .body("executionStatus", is("SUCCEEDED"))
        .body("canaryAnalysisExecutionResult.hasWarnings", is(false))
        .body("canaryAnalysisExecutionResult.didPassThresholds", is(true))
        .body(
            "canaryAnalysisExecutionResult.canaryScoreMessage",
            is("Final canary score 100.0 met or exceeded the pass score threshold."))
        .body(metricResultPath("CPU usage for service") + ".classification", is("Pass"));
  }

  @Test
  public void canaryAnalysisIsFailed() {
    String canaryAnalysisExecutionId =
        steps.createCanaryAnalysis(
            "cpu-marginal-analysis-case",
            "prometheus-account",
            "minio-store-account",
            "canary-configs/prometheus/integration-test-cpu.json");

    ValidatableResponse response =
        steps.waitUntilCanaryAnalysisCompleted(canaryAnalysisExecutionId);

    response
        .body("executionStatus", is("TERMINAL"))
        .body("canaryAnalysisExecutionResult.hasWarnings", is(false))
        .body("canaryAnalysisExecutionResult.didPassThresholds", is(false))
        .body(
            "canaryAnalysisExecutionResult.canaryScoreMessage",
            is("Final canary score 0.0 is not above the marginal score threshold."))
        .body(metricResultPath("CPU usage for service") + ".classification", is("High"));
  }

  @Test
  public void mutedMetricsAreNotTakenIntoFinalScore() {
    String canaryAnalysisExecutionId =
        steps.createCanaryAnalysis(
            "muted-metric-analysis-case",
            "prometheus-account",
            "minio-store-account",
            "canary-configs/prometheus/muted-metric.json");

    ValidatableResponse response =
        steps.waitUntilCanaryAnalysisCompleted(canaryAnalysisExecutionId);

    response
        .body("executionStatus", is("SUCCEEDED"))
        .body("canaryAnalysisExecutionResult.hasWarnings", is(false))
        .body("canaryAnalysisExecutionResult.didPassThresholds", is(true))
        .body(metricResultPath("Failing metric") + ".classification", is("High"))
        .body(metricResultPath("Successful metric") + ".classification", is("Pass"));
  }

  private String metricResultPath(String metricName) {
    return "canaryAnalysisExecutionResult.canaryExecutionResults[0].result.judgeResult.results.find { it.name == '"
        + metricName
        + "' }";
  }
}
