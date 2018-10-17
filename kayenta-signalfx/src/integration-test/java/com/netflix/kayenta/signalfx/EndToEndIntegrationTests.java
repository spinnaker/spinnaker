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

package com.netflix.kayenta.signalfx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.kayenta.Main;
import com.netflix.kayenta.canary.CanaryAdhocExecutionRequest;
import com.netflix.kayenta.canary.CanaryClassifierThresholdsConfig;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryExecutionRequest;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.CanaryScopePair;
import io.restassured.response.ValidatableResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.time.Instant;

import static com.netflix.kayenta.config.SignalFxMockServiceReportingConfig.CONTROL_SCOPE_NAME;
import static com.netflix.kayenta.config.SignalFxMockServiceReportingConfig.HEALTHY_EXPERIMENT_SCOPE_NAME;
import static com.netflix.kayenta.config.SignalFxMockServiceReportingConfig.SIGNAL_FX_SCOPE_IDENTIFYING_DIMENSION_NAME;
import static com.netflix.kayenta.config.SignalFxMockServiceReportingConfig.UNHEALTHY_EXPERIMENT_SCOPE_NAME;
import static com.netflix.kayenta.signalfx.canary.SignalFxCanaryScopeFactory.SCOPE_KEY_KEY;
import static io.restassured.RestAssured.*;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.hamcrest.Matchers.*;

/**
 * End to end integration tests
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = Main.class
)
@Slf4j
public class EndToEndIntegrationTests {

  public static final int CANARY_WINDOW_IN_MINUTES = 1;

  @Autowired
  private ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  private String testId;

  @Autowired
  private Instant metricsReportingStartTime;

  @LocalServerPort
  protected int serverPort;

  private String getUriTemplate() {
    return "http://localhost:" + serverPort + "%s";
  }

  @Test
  public void test_that_signalfx_can_be_used_as_a_data_source_for_a_canary_execution_healthy() throws IOException {
    ValidatableResponse response = doCanaryExec(HEALTHY_EXPERIMENT_SCOPE_NAME);
    response.body("result.judgeResult.score.classification", is("Pass"));
  }

  @Test
  public void test_that_signalfx_can_be_used_as_a_data_source_for_a_canary_execution_unhealthy() throws IOException {
    ValidatableResponse response = doCanaryExec(UNHEALTHY_EXPERIMENT_SCOPE_NAME);
    response.body("result.judgeResult.score.classification", is("Fail"));
    response.body("result.judgeResult.score.classificationReason", containsString("Bad Request Rate for /v1/some-endpoint"));
  }

  private ValidatableResponse doCanaryExec(String expScope) throws IOException {
    // Build the Canary Adhoc Execution Request for our test
    CanaryAdhocExecutionRequest request = new CanaryAdhocExecutionRequest();

    CanaryConfig canaryConfig = objectMapper.readValue(getClass().getClassLoader()
        .getResourceAsStream("integration-test-canary-config.json"), CanaryConfig.class);

    request.setCanaryConfig(canaryConfig);

    CanaryExecutionRequest executionRequest = new CanaryExecutionRequest();
    CanaryClassifierThresholdsConfig canaryClassifierThresholdsConfig = CanaryClassifierThresholdsConfig.builder()
        .marginal(50D).pass(75D).build();
    executionRequest.setThresholds(canaryClassifierThresholdsConfig);

    Instant end = metricsReportingStartTime.plus(CANARY_WINDOW_IN_MINUTES, MINUTES);

    CanaryScope control = new CanaryScope()
        .setScope(CONTROL_SCOPE_NAME)
        .setExtendedScopeParams(ImmutableMap.of(
            SCOPE_KEY_KEY, SIGNAL_FX_SCOPE_IDENTIFYING_DIMENSION_NAME,
            "test-id", testId
        ))
        .setLocation("us-west-2")
        .setStep(1l)
        .setStart(metricsReportingStartTime)
        .setEnd(end);

    CanaryScope experiment = new CanaryScope()
        .setScope(expScope)
        .setExtendedScopeParams(ImmutableMap.of(
            SCOPE_KEY_KEY, SIGNAL_FX_SCOPE_IDENTIFYING_DIMENSION_NAME,
            "test-id", testId
        ))
        .setLocation("us-west-2")
        .setStep(1l)
        .setStart(metricsReportingStartTime)
        .setEnd(end);

    CanaryScopePair canaryScopePair = new CanaryScopePair();
    canaryScopePair.setControlScope(control);
    canaryScopePair.setExperimentScope(experiment);
    executionRequest.setScopes(ImmutableMap.of("default", canaryScopePair));
    request.setExecutionRequest(executionRequest);

    // trigger a canary stage execution with the request
    ValidatableResponse canaryExRes =
        given()
            .contentType("application/json")
            .queryParam("metricsAccountName", "sfx-integration-test-account")
            .queryParam("storageAccountName", "in-memory-store")
            .body(request)
        .when()
            .post(String.format(getUriTemplate(), "/canary"))
        .then()
            .log().ifValidationFails()
            .statusCode(200);

    String canaryExecutionId = canaryExRes.extract().body().jsonPath().getString("canaryExecutionId");

    // poll for the stage to complete
    ValidatableResponse response;
    do {
      response = when().get(String.format(getUriTemplate(), "/canary/" + canaryExecutionId))
          .then().statusCode(200);
    } while (!response.extract().body().jsonPath().getBoolean("complete"));

    // verify the results are as expected
    return response.log().everything(true).body("status", is("succeeded"));
  }
}
