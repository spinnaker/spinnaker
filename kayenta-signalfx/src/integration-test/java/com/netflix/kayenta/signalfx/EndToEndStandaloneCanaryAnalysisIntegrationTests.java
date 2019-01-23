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
 */

package com.netflix.kayenta.signalfx;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.kayenta.canary.CanaryClassifierThresholdsConfig;
import com.netflix.kayenta.canaryanalysis.domain.CanaryAnalysisAdhocExecutionRequest;
import com.netflix.kayenta.canaryanalysis.domain.CanaryAnalysisExecutionRequest;
import com.netflix.kayenta.canaryanalysis.domain.CanaryAnalysisExecutionRequestScope;
import com.netflix.kayenta.canaryanalysis.domain.StageMetadata;
import io.restassured.response.ValidatableResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.netflix.kayenta.config.SignalFxMockServiceReportingConfig.CONTROL_SCOPE_NAME;
import static com.netflix.kayenta.config.SignalFxMockServiceReportingConfig.HEALTHY_EXPERIMENT_SCOPE_NAME;
import static com.netflix.kayenta.config.SignalFxMockServiceReportingConfig.SIGNAL_FX_SCOPE_IDENTIFYING_DIMENSION_NAME;
import static com.netflix.kayenta.signalfx.canary.SignalFxCanaryScopeFactory.SCOPE_KEY_KEY;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.is;

@Slf4j
public class EndToEndStandaloneCanaryAnalysisIntegrationTests extends BaseSignalFxIntegrationTest {

  @BeforeClass
  public static void beforeClass() {
    System.setProperty("block.for.metrics", System.getProperty("block.for.metrics", "false"));
  }

  @Test
  public void test_that_a_canary_analysis_execution_can_be_created_healthy() throws InterruptedException {

    CanaryAnalysisAdhocExecutionRequest request = new CanaryAnalysisAdhocExecutionRequest();

    request.setCanaryConfig(integrationTestCanaryConfig);

    CanaryAnalysisExecutionRequest executionRequest = CanaryAnalysisExecutionRequest.builder()
        .scopes(ImmutableList.of(
            CanaryAnalysisExecutionRequestScope.builder()
                .controlScope(CONTROL_SCOPE_NAME)
                .controlLocation(LOCATION)
                .experimentScope(HEALTHY_EXPERIMENT_SCOPE_NAME)
                .experimentLocation(LOCATION)
                .extendedScopeParams(ImmutableMap.of(
                    TEST_ID, testId
                ))
                .startTimeIso(metricsReportingStartTime.toString())
                .step(1L)
                .build()
        ))
        .thresholds(CanaryClassifierThresholdsConfig.builder().marginal(50D).pass(75D).build())
        .lifetimeDurationMins(3L)
        .analysisIntervalMins(1L)
        .build();

    request.setExecutionRequest(executionRequest);

    // execute the request
    ValidatableResponse canaryAnalysisExRes =
        given()
            .header("Content-Type", "application/json")
            .queryParam(METRICS_ACCOUNT_NAME_QUERY_KEY, METRICS_ACCOUNT_NAME)
            .queryParam(STORAGE_ACCOUNT_NAME_QUERY_KEY, STORAGE_ACCOUNT_NAME)
            .body(request)
        .when()
            .log().all(true)
            .post(String.format(getUriTemplate(), "/standalone_canary_analysis"))
        .then()
            .log().all()
            .statusCode(200);

    String canaryAnalysisExecutionId = canaryAnalysisExRes.extract().body().jsonPath().getString("canaryAnalysisExecutionId");

    // poll for the stage to complete
    ValidatableResponse response;
    do {
      response = when().get(String.format(getUriTemplate(), "/standalone_canary_analysis/" + canaryAnalysisExecutionId))
          .then().statusCode(200);
      Thread.sleep(5000);
      log.info("Stage Status");
      response.extract().body().jsonPath().getList("stageStatus", StageMetadata.class)
          .forEach(stage -> log.info(stage.toString()));
    } while (!response.extract().body().jsonPath().getBoolean("complete"));

    // verify the results are as expected
    response.log().everything(true).body("status", is("succeeded"));
  }
}
