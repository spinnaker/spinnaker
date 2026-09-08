/*
 * Copyright 2024 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.google.provider.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.*;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GoogleRegionalExternalHttpLoadBalancerCachingAgentTest {

  private GoogleRegionalExternalHttpLoadBalancerCachingAgent agent;
  private GoogleNamedAccountCredentials credentials;
  private ObjectMapper objectMapper;
  private Registry registry;
  private static final String PROJECT = "test-project";
  private static final String REGION = "us-central1";
  private static final String ACCOUNT_NAME = "test-account";

  @BeforeEach
  void setUp() throws IOException {
    credentials = mock(GoogleNamedAccountCredentials.class);
    Compute compute = mock(Compute.class);
    when(credentials.getCompute()).thenReturn(compute);
    when(credentials.getProject()).thenReturn(PROJECT);
    when(credentials.getName()).thenReturn(ACCOUNT_NAME);

    objectMapper = new ObjectMapper();
    registry = new NoopRegistry();

    agent =
        new GoogleRegionalExternalHttpLoadBalancerCachingAgent(
            "user-agent", credentials, objectMapper, registry, REGION);
  }

  @Test
  void testAgentType() {
    assertThat(agent.getAgentType())
        .isEqualTo(
            ACCOUNT_NAME + "/" + REGION + "/GoogleRegionalExternalHttpLoadBalancerCachingAgent");
  }

  @Test
  void testRegionIsSet() {
    assertThat(agent.getRegion()).isEqualTo(REGION);
  }

  @Test
  void handleHealthCheck_withHttpHealthCheck() throws Exception {
    // Given
    HealthCheck healthCheck = buildBaseHealthCheck("http-hc", REGION);
    HTTPHealthCheck httpHealthCheck = new HTTPHealthCheck();
    httpHealthCheck.setPort(8080);
    httpHealthCheck.setRequestPath("/health");
    healthCheck.setHttpHealthCheck(httpHealthCheck);

    List<GoogleBackendService> googleBackendServices = new ArrayList<>();
    googleBackendServices.add(new GoogleBackendService());

    // When
    invokeHandleHealthCheck(healthCheck, googleBackendServices);

    // Then
    GoogleBackendService backendService = googleBackendServices.get(0);
    GoogleHealthCheck ghc = backendService.getHealthCheck();
    assertThat(ghc).isNotNull();
    assertThat(ghc.getName()).isEqualTo("http-hc");
    assertThat(ghc.getPort()).isEqualTo(8080);
    assertThat(ghc.getRequestPath()).isEqualTo("/health");
    assertThat(ghc.getHealthCheckType()).isEqualTo(GoogleHealthCheck.HealthCheckType.HTTP);
    assertThat(ghc.getRegion()).isEqualTo(REGION);
  }

  @Test
  void handleHealthCheck_withHttpsHealthCheck() throws Exception {
    // Given
    HealthCheck healthCheck = buildBaseHealthCheck("https-hc", REGION);
    HTTPSHealthCheck httpsHealthCheck = new HTTPSHealthCheck();
    httpsHealthCheck.setPort(8443);
    httpsHealthCheck.setRequestPath("/secure");
    healthCheck.setHttpsHealthCheck(httpsHealthCheck);

    List<GoogleBackendService> googleBackendServices = new ArrayList<>();
    googleBackendServices.add(new GoogleBackendService());

    // When
    invokeHandleHealthCheck(healthCheck, googleBackendServices);

    // Then
    GoogleBackendService backendService = googleBackendServices.get(0);
    GoogleHealthCheck ghc = backendService.getHealthCheck();
    assertThat(ghc).isNotNull();
    assertThat(ghc.getName()).isEqualTo("https-hc");
    assertThat(ghc.getPort()).isEqualTo(8443);
    assertThat(ghc.getRequestPath()).isEqualTo("/secure");
    assertThat(ghc.getHealthCheckType()).isEqualTo(GoogleHealthCheck.HealthCheckType.HTTPS);
  }

  @Test
  void handleHealthCheck_withTcpHealthCheck() throws Exception {
    // Given
    HealthCheck healthCheck = buildBaseHealthCheck("tcp-hc", REGION);
    TCPHealthCheck tcpHealthCheck = new TCPHealthCheck();
    tcpHealthCheck.setPort(3306);
    healthCheck.setTcpHealthCheck(tcpHealthCheck);

    List<GoogleBackendService> googleBackendServices = new ArrayList<>();
    googleBackendServices.add(new GoogleBackendService());

    // When
    invokeHandleHealthCheck(healthCheck, googleBackendServices);

    // Then
    GoogleBackendService backendService = googleBackendServices.get(0);
    GoogleHealthCheck ghc = backendService.getHealthCheck();
    assertThat(ghc).isNotNull();
    assertThat(ghc.getName()).isEqualTo("tcp-hc");
    assertThat(ghc.getPort()).isEqualTo(3306);
    assertThat(ghc.getHealthCheckType()).isEqualTo(GoogleHealthCheck.HealthCheckType.TCP);
  }

  @Test
  void getFirstSslCertificateName_withCertificates() {
    TargetHttpsProxy proxy = new TargetHttpsProxy();
    List<String> certs = new ArrayList<>();
    certs.add(
        "https://compute.googleapis.com/compute/v1/projects/test-project/global/sslCertificates/my-cert");
    certs.add(
        "https://compute.googleapis.com/compute/v1/projects/test-project/global/sslCertificates/backup-cert");
    proxy.setSslCertificates(certs);

    String result =
        AbstractGoogleRegionalHttpLoadBalancerCachingAgent.getFirstSslCertificateName(proxy);

    assertThat(result).isEqualTo("my-cert");
  }

  @Test
  void getFirstSslCertificateName_withNoCertificates() {
    TargetHttpsProxy proxy = new TargetHttpsProxy();

    String result =
        AbstractGoogleRegionalHttpLoadBalancerCachingAgent.getFirstSslCertificateName(proxy);

    assertThat(result).isNull();
  }

  @Test
  void getFirstSslCertificateName_withEmptyList() {
    TargetHttpsProxy proxy = new TargetHttpsProxy();
    proxy.setSslCertificates(new ArrayList<>());

    String result =
        AbstractGoogleRegionalHttpLoadBalancerCachingAgent.getFirstSslCertificateName(proxy);

    assertThat(result).isNull();
  }

  private static HealthCheck buildBaseHealthCheck(String name, String region) {
    HealthCheck hc = new HealthCheck();
    hc.setName(name);
    hc.setSelfLink(
        "https://compute.googleapis.com/compute/v1/projects/test/regions/"
            + region
            + "/healthChecks/"
            + name);
    hc.setRegion(region);
    hc.setCheckIntervalSec(30);
    hc.setTimeoutSec(5);
    hc.setHealthyThreshold(2);
    hc.setUnhealthyThreshold(3);
    return hc;
  }

  private void invokeHandleHealthCheck(
      HealthCheck healthCheck, List<GoogleBackendService> backendServices) throws Exception {
    Method method =
        AbstractGoogleRegionalHttpLoadBalancerCachingAgent.class.getDeclaredMethod(
            "handleHealthCheck", HealthCheck.class, List.class);
    method.setAccessible(true);
    method.invoke(null, healthCheck, backendServices);
  }
}
