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
package com.netflix.kayenta.steps;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;

import com.google.common.collect.ImmutableMap;
import com.netflix.kayenta.canary.CanaryClassifierThresholdsConfig;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.domain.standalonecanaryanalysis.CanaryAnalysisAdhocExecutionRequest;
import com.netflix.kayenta.domain.standalonecanaryanalysis.CanaryAnalysisExecutionRequest;
import com.netflix.kayenta.domain.standalonecanaryanalysis.CanaryAnalysisExecutionRequestScope;
import com.netflix.kayenta.metrics.CanaryAnalysisCasesConfigurationProperties;
import com.netflix.kayenta.utils.CanaryConfigReader;
import io.restassured.response.ValidatableResponse;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public class StandaloneCanaryAnalysisSteps {

  private final int serverPort;
  private final CanaryAnalysisCasesConfigurationProperties cases;

  public String createCanaryAnalysis(
      String caseName,
      String metricsAccountName,
      String storageAccountName,
      String configFileName) {
    CanaryAnalysisCasesConfigurationProperties.AnalysisConfiguration caseConfig =
        cases.get(caseName);
    CanaryAnalysisAdhocExecutionRequest request = prepareRequest(configFileName, caseConfig);

    ValidatableResponse createAnalysisResponse =
        given()
            .port(serverPort)
            .header("Content-Type", "application/json")
            .queryParam("metricsAccountName", metricsAccountName)
            .queryParam("storageAccountName", storageAccountName)
            .queryParam("application", "kayenta-integration-tests")
            .queryParam("user", "asmirnova@playtika.com")
            .body(request)
            .when()
            .log()
            .all(true)
            .post("/standalone_canary_analysis")
            .then()
            .log()
            .all(true)
            .statusCode(HttpStatus.OK.value());

    return createAnalysisResponse.extract().jsonPath().getString("canaryAnalysisExecutionId");
  }

  public ValidatableResponse waitUntilCanaryAnalysisCompleted(String canaryAnalysisExecutionId) {
    await()
        .pollInterval(30, TimeUnit.SECONDS)
        .timeout(10, TimeUnit.MINUTES)
        .untilAsserted(
            () ->
                getCanaryAnalysisExecution(canaryAnalysisExecutionId)
                    .statusCode(HttpStatus.OK.value())
                    .log()
                    .all(true)
                    .body("complete", is(true)));
    return getCanaryAnalysisExecution(canaryAnalysisExecutionId);
  }

  public ValidatableResponse getCanaryAnalysisExecution(String canaryAnalysisExecutionId) {
    return given()
        .port(serverPort)
        .get("/standalone_canary_analysis/" + canaryAnalysisExecutionId)
        .then();
  }

  private CanaryAnalysisAdhocExecutionRequest prepareRequest(
      String canaryConfigFileName,
      CanaryAnalysisCasesConfigurationProperties.AnalysisConfiguration caseConfig) {
    CanaryConfig canaryConfig = CanaryConfigReader.getCanaryConfig(canaryConfigFileName);

    CanaryAnalysisExecutionRequest executionRequest =
        CanaryAnalysisExecutionRequest.builder()
            .scopes(
                Arrays.asList(
                    CanaryAnalysisExecutionRequestScope.builder()
                        .controlScope(caseConfig.getControl().getScope())
                        .experimentScope(caseConfig.getExperiment().getScope())
                        .extendedScopeParams(
                            ImmutableMap.of("namespace", caseConfig.getNamespace()))
                        .step(1L) // step for prometheus metrics query
                        .build()))
            .thresholds(CanaryClassifierThresholdsConfig.builder().marginal(50D).pass(75D).build())
            .lifetimeDurationMins(caseConfig.getLifetimeDurationMinutes())
            .analysisIntervalMins(caseConfig.getAnalysisIntervalMinutes())
            .build();

    CanaryAnalysisAdhocExecutionRequest request = new CanaryAnalysisAdhocExecutionRequest();
    request.setCanaryConfig(canaryConfig);
    request.setExecutionRequest(executionRequest);
    return request;
  }
}
