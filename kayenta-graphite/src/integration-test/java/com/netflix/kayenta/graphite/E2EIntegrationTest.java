/*
 * Copyright 2018 Snap Inc.
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

package com.netflix.kayenta.graphite;

import static com.netflix.kayenta.config.GraphiteIntegrationTestConfig.CONTROL_SCOPE_NAME;
import static com.netflix.kayenta.config.GraphiteIntegrationTestConfig.EXPERIMENT_SCOPE_HEALTHY;
import static com.netflix.kayenta.config.GraphiteIntegrationTestConfig.EXPERIMENT_SCOPE_UNHEALTHY;

import static org.hamcrest.core.Is.is;

import static java.time.temporal.ChronoUnit.MINUTES;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.kayenta.Main;
import com.netflix.kayenta.canary.CanaryAdhocExecutionRequest;
import com.netflix.kayenta.canary.CanaryClassifierThresholdsConfig;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryExecutionRequest;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.CanaryScopePair;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;

import io.restassured.response.ValidatableResponse;

@RunWith(SpringRunner.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = Main.class
)

public class E2EIntegrationTest {

    public static final int CANARY_WINDOW_IN_MINUTES = 1;

    @Autowired
    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private Instant metricsReportingStartTime;

    @LocalServerPort
    protected int serverPort;

    private String getUrl(String path) throws MalformedURLException {
        URL url = new URL("http", "localhost", serverPort, path);
        return url.toString();
    }

    @Test
    public void test_healthy_graphite_canary_execution() throws IOException {
        ValidatableResponse response = doCanaryExec(EXPERIMENT_SCOPE_HEALTHY);
        response.body("result.judgeResult.score.classification", is("Pass"));
    }

    @Test
    public void test_unhealthy_graphite_canary_execution() throws IOException {
        ValidatableResponse response = doCanaryExec(EXPERIMENT_SCOPE_UNHEALTHY);
        response.body("result.judgeResult.score.classification", is("Fail"));

    }

    private ValidatableResponse doCanaryExec(String scope) throws IOException {
        String canaryConfigJson = System.getProperty("canary.config");
        Double marginal = Double.parseDouble(System.getProperty("canary.marginal"));
        Double pass = Double.parseDouble(System.getProperty("canary.pass"));

        CanaryAdhocExecutionRequest request = new CanaryAdhocExecutionRequest();
        CanaryConfig canaryConfig = objectMapper.readValue(getClass().getClassLoader()
            .getResourceAsStream(canaryConfigJson), CanaryConfig.class);
        request.setCanaryConfig(canaryConfig);

        CanaryExecutionRequest executionRequest = new CanaryExecutionRequest();
        CanaryClassifierThresholdsConfig canaryClassifierThresholdsConfig = CanaryClassifierThresholdsConfig.builder()
            .marginal(marginal).pass(pass).build();
        executionRequest.setThresholds(canaryClassifierThresholdsConfig);

        Instant end = metricsReportingStartTime.plus(CANARY_WINDOW_IN_MINUTES, MINUTES);

        CanaryScope control = new CanaryScope()
            .setScope(CONTROL_SCOPE_NAME)
            .setStart(metricsReportingStartTime)
            .setEnd(end);

        CanaryScope experiment = new CanaryScope()
            .setScope(scope)
            .setStart(metricsReportingStartTime)
            .setEnd(end);

        CanaryScopePair canaryScopePair = CanaryScopePair.builder()
          .controlScope(control)
          .experimentScope(experiment)
          .build();
        executionRequest.setScopes(ImmutableMap.of("default", canaryScopePair));
        request.setExecutionRequest(executionRequest);

        ValidatableResponse canaryExRes =
            given()
                .contentType("application/json")
                .queryParam("metricsAccountName", "my-graphite-account")
                .queryParam("storageAccountName", "in-memory-store")
                .body(request)
                .when()
                .post(getUrl("/canary"))
                .then()
                .log().ifValidationFails()
                .statusCode(200);

        String canaryExecutionId = canaryExRes.extract().body().jsonPath().getString("canaryExecutionId");
        ValidatableResponse response;
        do {
            response = when().get(String.format(getUrl("/canary/" + canaryExecutionId)))
                .then().statusCode(200);
        } while (!response.extract().body().jsonPath().getBoolean("complete"));

        // verify the results are as expected
        return response.log().everything(true).body("status", is("succeeded"));
    }
}
