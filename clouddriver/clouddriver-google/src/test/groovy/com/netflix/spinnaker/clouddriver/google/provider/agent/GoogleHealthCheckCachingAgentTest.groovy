/*
 * Copyright 2019 Google, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck

import static org.assertj.core.api.Assertions.assertThat

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.*
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GoogleHealthCheckCachingAgentTest {

  private static final String ACCOUNT_NAME = "partypups"
  private static final String PROJECT = "myproject"
  private static final String REGION = "myregion"
  private static final String REGION_URL = "http://compute/regions/" + REGION
  private static final String ZONE = REGION + "-myzone"
  private static final String ZONE_URL = "http://compute/zones/" + ZONE

  private ObjectMapper objectMapper
  private GoogleHealthCheckCachingAgent healthCheckAgent

  @BeforeEach
  void createTestObjects() {
    objectMapper = new ObjectMapper()

    Compute compute = new StubComputeFactory().create()
    GoogleNamedAccountCredentials credentials =
      new GoogleNamedAccountCredentials.Builder()
        .project(PROJECT)
        .name(ACCOUNT_NAME)
        .compute(compute)
        .regionToZonesMap(ImmutableMap.of(REGION, ImmutableList.of(ZONE)))
        .build()
    healthCheckAgent = new GoogleHealthCheckCachingAgent(
      "app-name",
      credentials,
      objectMapper,
      new DefaultRegistry(),
    )
  }

  private static HealthCheck buildBaseHealthCheck(String name, String region) {
    HealthCheck hc = new HealthCheck()
    hc.setName(name)
    hc.setSelfLink("http://selflink")
    hc.setRegion(region)
    hc.setCheckIntervalSec(60)
    hc.setTimeoutSec(10)
    hc.setHealthyThreshold(1)
    hc.setUnhealthyThreshold(3)
    return hc
  }

  @Test
  void createsValidHttpHealthCheck() {
    HTTPHealthCheck httpHealthCheck = new HTTPHealthCheck()
    httpHealthCheck.setPort(1234)
    httpHealthCheck.setRequestPath("/healthz")

    HealthCheck hc = buildBaseHealthCheck("valid", REGION)
    hc.setHttpHealthCheck(httpHealthCheck)
    hc.setType("HTTP")

    GoogleHealthCheck ghc = healthCheckAgent.toGoogleHealthCheck(hc, REGION)
    assertThat(ghc.getPort()).isEqualTo(1234)
    assertThat(ghc.getRegion()).isEqualTo(REGION)
    assertThat(ghc.getRequestPath()).isEqualTo("/healthz")
    assertThat(ghc.getHealthCheckType()).isEqualTo(GoogleHealthCheck.HealthCheckType.HTTP)
  }

  @Test
  void handlesHttpHealthCheckWithoutPort() {
    HTTPHealthCheck httpHealthCheck = new HTTPHealthCheck()
    httpHealthCheck.setRequestPath("/healthz")

    HealthCheck hc = buildBaseHealthCheck("no-port", REGION)
    hc.setHttpHealthCheck(httpHealthCheck)
    hc.setType("HTTP")

    GoogleHealthCheck ghc = healthCheckAgent.toGoogleHealthCheck(hc, REGION)
    assertThat(ghc).isNull()
  }
}
