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
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class GraphiteStandaloneCanaryAnalysisTest extends BaseIntegrationTest {

  @Autowired protected StandaloneCanaryAnalysisSteps steps;

  @Test
  public void canaryAnalysisIsSuccessful() throws InterruptedException {
    int retries = 4;
    ValidatableResponse response = null;
    while (retries > 0) {
      String canaryAnalysisExecutionId =
          steps.createCanaryAnalysis(
              "cpu-successful-analysis-case",
              "graphite-account",
              "in-memory-store-account",
              "canary-configs/graphite/integration-test-cpu.json");

      response = steps.waitUntilCanaryAnalysisCompleted(canaryAnalysisExecutionId);
      if (!"SUCCEEDED".equals(response.extract().path("executionStatus"))) {
        log.warn("Validation failed so retrying . . ");
        Thread.sleep(30000);
        retries--;
        continue;
      }
      break;
    }
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
