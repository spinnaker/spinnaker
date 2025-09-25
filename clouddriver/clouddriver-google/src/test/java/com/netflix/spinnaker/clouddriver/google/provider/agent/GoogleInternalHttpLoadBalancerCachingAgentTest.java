/*
 * Copyright 2025 Harness, Inc.
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

import com.google.api.services.compute.model.*;
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class GoogleInternalHttpLoadBalancerCachingAgentTest {

  private static HealthCheck buildBaseHealthCheck(String name, String region) {
    HealthCheck hc = new HealthCheck();
    hc.setName(name);
    hc.setSelfLink(
        "https://compute.googleapis.com/compute/v1/projects/test/global/healthChecks/" + name);
    hc.setRegion(region);
    hc.setCheckIntervalSec(30);
    hc.setTimeoutSec(5);
    hc.setHealthyThreshold(2);
    hc.setUnhealthyThreshold(3);
    return hc;
  }

  @Test
  void handleHealthCheck_withHttpHealthCheck() throws Exception {
    // Given
    HealthCheck healthCheck = buildBaseHealthCheck("http-hc", "us-central1");
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
    assertThat(ghc.getRegion()).isEqualTo("us-central1");
  }

  @Test
  void handleHealthCheck_withHttpsHealthCheck() throws Exception {
    // Given
    HealthCheck healthCheck = buildBaseHealthCheck("https-hc", "us-central1");
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
  void handleHealthCheck_withHttp2HealthCheck() throws Exception {
    // Given
    HealthCheck healthCheck = buildBaseHealthCheck("http2-hc", "us-central1");
    HTTP2HealthCheck http2HealthCheck = new HTTP2HealthCheck();
    http2HealthCheck.setPort(9000);
    http2HealthCheck.setRequestPath("/api/v2/health");
    healthCheck.setHttp2HealthCheck(http2HealthCheck);

    List<GoogleBackendService> googleBackendServices = new ArrayList<>();
    googleBackendServices.add(new GoogleBackendService());

    // When
    invokeHandleHealthCheck(healthCheck, googleBackendServices);

    // Then
    GoogleBackendService backendService = googleBackendServices.get(0);
    GoogleHealthCheck ghc = backendService.getHealthCheck();
    assertThat(ghc).isNotNull();
    assertThat(ghc.getName()).isEqualTo("http2-hc");
    assertThat(ghc.getPort()).isEqualTo(9000);
    assertThat(ghc.getRequestPath()).isEqualTo("/api/v2/health");
    assertThat(ghc.getHealthCheckType()).isEqualTo(GoogleHealthCheck.HealthCheckType.HTTP2);
  }

  @Test
  void handleHealthCheck_withGrpcHealthCheck() throws Exception {
    // Given
    HealthCheck healthCheck = buildBaseHealthCheck("grpc-hc", "us-central1");
    GRPCHealthCheck grpcHealthCheck = new GRPCHealthCheck();
    grpcHealthCheck.setPort(9090);
    grpcHealthCheck.setGrpcServiceName("com.example.HealthService");
    healthCheck.setGrpcHealthCheck(grpcHealthCheck);

    List<GoogleBackendService> googleBackendServices = new ArrayList<>();
    googleBackendServices.add(new GoogleBackendService());

    // When
    invokeHandleHealthCheck(healthCheck, googleBackendServices);

    // Then
    GoogleBackendService backendService = googleBackendServices.get(0);
    GoogleHealthCheck ghc = backendService.getHealthCheck();
    assertThat(ghc).isNotNull();
    assertThat(ghc.getName()).isEqualTo("grpc-hc");
    assertThat(ghc.getPort()).isEqualTo(9090);
    assertThat(ghc.getRequestPath()).isEqualTo("com.example.HealthService");
    assertThat(ghc.getHealthCheckType()).isEqualTo(GoogleHealthCheck.HealthCheckType.GRPC);
  }

  @Test
  void handleHealthCheck_withTcpHealthCheck() throws Exception {
    // Given
    HealthCheck healthCheck = buildBaseHealthCheck("tcp-hc", "us-central1");
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
    assertThat(ghc.getRequestPath()).isNull();
    assertThat(ghc.getHealthCheckType()).isEqualTo(GoogleHealthCheck.HealthCheckType.TCP);
  }

  @Test
  void handleHealthCheck_withSslHealthCheck() throws Exception {
    // Given
    HealthCheck healthCheck = buildBaseHealthCheck("ssl-hc", "us-central1");
    SSLHealthCheck sslHealthCheck = new SSLHealthCheck();
    sslHealthCheck.setPort(636);
    healthCheck.setSslHealthCheck(sslHealthCheck);

    List<GoogleBackendService> googleBackendServices = new ArrayList<>();
    googleBackendServices.add(new GoogleBackendService());

    // When
    invokeHandleHealthCheck(healthCheck, googleBackendServices);

    // Then
    GoogleBackendService backendService = googleBackendServices.get(0);
    GoogleHealthCheck ghc = backendService.getHealthCheck();
    assertThat(ghc).isNotNull();
    assertThat(ghc.getName()).isEqualTo("ssl-hc");
    assertThat(ghc.getPort()).isEqualTo(636);
    assertThat(ghc.getRequestPath()).isNull();
    assertThat(ghc.getHealthCheckType()).isEqualTo(GoogleHealthCheck.HealthCheckType.SSL);
  }

  @Test
  void handleHealthCheck_withNullHealthCheck() throws Exception {
    // Given
    List<GoogleBackendService> googleBackendServices = new ArrayList<>();
    googleBackendServices.add(new GoogleBackendService());

    // When
    invokeHandleHealthCheck(null, googleBackendServices);

    // Then
    GoogleBackendService backendService = googleBackendServices.get(0);
    assertThat(backendService.getHealthCheck()).isNull();
  }

  @Test
  void handleHealthCheck_withMultipleBackendServices() throws Exception {
    // Given
    HealthCheck healthCheck = buildBaseHealthCheck("multi-hc", "us-central1");
    HTTP2HealthCheck http2HealthCheck = new HTTP2HealthCheck();
    http2HealthCheck.setPort(8080);
    http2HealthCheck.setRequestPath("/health");
    healthCheck.setHttp2HealthCheck(http2HealthCheck);

    List<GoogleBackendService> googleBackendServices = new ArrayList<>();
    googleBackendServices.add(new GoogleBackendService());
    googleBackendServices.add(new GoogleBackendService());
    googleBackendServices.add(new GoogleBackendService());

    // When
    invokeHandleHealthCheck(healthCheck, googleBackendServices);

    // Then
    for (GoogleBackendService backendService : googleBackendServices) {
      GoogleHealthCheck ghc = backendService.getHealthCheck();
      assertThat(ghc).isNotNull();
      assertThat(ghc.getName()).isEqualTo("multi-hc");
      assertThat(ghc.getPort()).isEqualTo(8080);
      assertThat(ghc.getRequestPath()).isEqualTo("/health");
      assertThat(ghc.getHealthCheckType()).isEqualTo(GoogleHealthCheck.HealthCheckType.HTTP2);
    }
  }

  /** Helper method to invoke the private static handleHealthCheck method using reflection */
  private void invokeHandleHealthCheck(
      HealthCheck healthCheck, List<GoogleBackendService> googleBackendServices) throws Exception {
    Method handleHealthCheckMethod =
        GoogleInternalHttpLoadBalancerCachingAgent.class.getDeclaredMethod(
            "handleHealthCheck", HealthCheck.class, List.class);
    handleHealthCheckMethod.setAccessible(true);
    handleHealthCheckMethod.invoke(null, healthCheck, googleBackendServices);
  }
}
