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
package com.netflix.kayenta.tests;

import static com.netflix.kayenta.utils.AwaitilityUtils.awaitThirtySecondsUntil;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@Slf4j
public class ManagementTest extends BaseIntegrationTest {

  @Test
  public void prometheusTargetsAreAllReportingUp() throws InterruptedException {
    int retries = 30; // wait up to 30 seconds
    String prometheusPortStr = null;

    while (retries-- > 0) {
      prometheusPortStr = environment.getProperty("embedded.prometheus.port");
      if (prometheusPortStr != null) {
        break;
      }
      Thread.sleep(1000);
    }

    if (prometheusPortStr == null) {
      throw new IllegalStateException("embedded.prometheus.port not set even after waiting!");
    }

    int prometheusPort = Integer.parseInt(prometheusPortStr);

    System.out.println("Prometheus Port: " + prometheusPort);

    awaitThirtySecondsUntil(
        () ->
            given()
                .port(prometheusPort)
                .get("/api/v1/targets")
                .prettyPeek()
                .then()
                .assertThat()
                .statusCode(HttpStatus.OK.value())
                .body("status", is("success"))
                .body("data.activeTargets[0].health", is("up")));
  }

  @Test
  public void healthIsUp() {
    awaitThirtySecondsUntil(
        () ->
            given()
                .port(getManagementPort())
                .get("/health")
                .prettyPeek()
                .then()
                .assertThat()
                .statusCode(HttpStatus.OK.value())
                .body("status", is("UP"))
                .body("components.canaryConfigIndexingAgent.status", is("UP"))
                .body("components.prometheus.status", is("UP"))
                .body("components.prometheus.details.'prometheus-account'.status", is("UP"))
                .body("components.redisHealth.status", is("UP")));
  }
}
