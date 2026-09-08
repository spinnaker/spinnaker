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

package com.netflix.spinnaker.clouddriver.google.model.loadbalancing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class GoogleRegionalExternalHttpLoadBalancerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void testTypeIsHttp() {
    GoogleRegionalExternalHttpLoadBalancer lb = new GoogleRegionalExternalHttpLoadBalancer();

    assertThat(lb.getType()).isEqualTo(GoogleLoadBalancerType.HTTP);
  }

  @Test
  void testLoadBalancingSchemeIsExternal() {
    GoogleRegionalExternalHttpLoadBalancer lb = new GoogleRegionalExternalHttpLoadBalancer();

    assertThat(lb.getLoadBalancingScheme()).isEqualTo(GoogleLoadBalancingScheme.EXTERNAL);
  }

  @Test
  void testBasicProperties() {
    GoogleRegionalExternalHttpLoadBalancer lb = new GoogleRegionalExternalHttpLoadBalancer();
    lb.setName("test-lb");
    lb.setAccount("test-account");
    lb.setRegion("us-central1");
    lb.setCreatedTime(1234567890L);
    lb.setIpAddress("10.0.0.1");
    lb.setIpProtocol("TCP");
    lb.setPortRange("80");

    assertThat(lb.getName()).isEqualTo("test-lb");
    assertThat(lb.getAccount()).isEqualTo("test-account");
    assertThat(lb.getRegion()).isEqualTo("us-central1");
    assertThat(lb.getCreatedTime()).isEqualTo(1234567890L);
    assertThat(lb.getIpAddress()).isEqualTo("10.0.0.1");
    assertThat(lb.getIpProtocol()).isEqualTo("TCP");
    assertThat(lb.getPortRange()).isEqualTo("80");
  }

  @Test
  void testHttpSpecificProperties() {
    GoogleRegionalExternalHttpLoadBalancer lb = new GoogleRegionalExternalHttpLoadBalancer();

    GoogleBackendService defaultService = new GoogleBackendService();
    defaultService.setName("default-backend");
    lb.setDefaultService(defaultService);

    List<GoogleHostRule> hostRules = new ArrayList<>();
    GoogleHostRule hostRule = new GoogleHostRule();
    hostRule.setHostPatterns(List.of("example.com"));
    hostRules.add(hostRule);
    lb.setHostRules(hostRules);

    lb.setCertificate("my-ssl-cert");
    lb.setCertificateMap("my-cert-map");
    lb.setUrlMapName("my-url-map");
    lb.setNetwork("default");
    lb.setSubnet("default-subnet");

    assertThat(lb.getDefaultService()).isNotNull();
    assertThat(lb.getDefaultService().getName()).isEqualTo("default-backend");
    assertThat(lb.getHostRules()).hasSize(1);
    assertThat(lb.getCertificate()).isEqualTo("my-ssl-cert");
    assertThat(lb.getCertificateMap()).isEqualTo("my-cert-map");
    assertThat(lb.getUrlMapName()).isEqualTo("my-url-map");
    assertThat(lb.getNetwork()).isEqualTo("default");
    assertThat(lb.getSubnet()).isEqualTo("default-subnet");
  }

  @Test
  void testViewCreation() {
    GoogleRegionalExternalHttpLoadBalancer lb = new GoogleRegionalExternalHttpLoadBalancer();
    lb.setName("test-lb");
    lb.setAccount("test-account");
    lb.setRegion("us-central1");
    lb.setIpAddress("10.0.0.1");
    lb.setUrlMapName("my-url-map");

    GoogleRegionalExternalHttpLoadBalancer.RegionalExternalHttpLbView view = lb.getView();

    assertThat(view).isNotNull();
    assertThat(view.getLoadBalancerType()).isEqualTo(GoogleLoadBalancerType.HTTP);
    assertThat(view.getLoadBalancingScheme()).isEqualTo(GoogleLoadBalancingScheme.EXTERNAL);
    assertThat(view.getName()).isEqualTo("test-lb");
    assertThat(view.getAccount()).isEqualTo("test-account");
    assertThat(view.getRegion()).isEqualTo("us-central1");
    assertThat(view.getIpAddress()).isEqualTo("10.0.0.1");
    assertThat(view.getUrlMapName()).isEqualTo("my-url-map");
  }

  @Test
  void testSerializationDeserialization() throws Exception {
    GoogleRegionalExternalHttpLoadBalancer lb = new GoogleRegionalExternalHttpLoadBalancer();
    lb.setName("test-lb");
    lb.setAccount("test-account");
    lb.setRegion("us-central1");
    lb.setIpAddress("10.0.0.1");
    lb.setUrlMapName("my-url-map");
    lb.setCertificate("my-cert");

    String json = objectMapper.writeValueAsString(lb);
    GoogleRegionalExternalHttpLoadBalancer deserialized =
        objectMapper.readValue(json, GoogleRegionalExternalHttpLoadBalancer.class);

    assertThat(deserialized.getName()).isEqualTo(lb.getName());
    assertThat(deserialized.getAccount()).isEqualTo(lb.getAccount());
    assertThat(deserialized.getRegion()).isEqualTo(lb.getRegion());
    assertThat(deserialized.getType()).isEqualTo(GoogleLoadBalancerType.HTTP);
    assertThat(deserialized.getLoadBalancingScheme()).isEqualTo(GoogleLoadBalancingScheme.EXTERNAL);
    assertThat(deserialized.getUrlMapName()).isEqualTo(lb.getUrlMapName());
    assertThat(deserialized.getCertificate()).isEqualTo(lb.getCertificate());
  }

  @Test
  void testViewWithBackendServicesAndHostRules() {
    GoogleRegionalExternalHttpLoadBalancer lb = new GoogleRegionalExternalHttpLoadBalancer();
    lb.setName("test-lb");

    GoogleBackendService defaultService = new GoogleBackendService();
    defaultService.setName("default-backend");
    lb.setDefaultService(defaultService);

    GooglePathMatcher pathMatcher = new GooglePathMatcher();
    pathMatcher.setDefaultService(defaultService);

    GooglePathRule pathRule = new GooglePathRule();
    pathRule.setPaths(List.of("/api/*"));
    GoogleBackendService apiBackend = new GoogleBackendService();
    apiBackend.setName("api-backend");
    pathRule.setBackendService(apiBackend);
    pathMatcher.setPathRules(List.of(pathRule));

    GoogleHostRule hostRule = new GoogleHostRule();
    hostRule.setHostPatterns(List.of("*.example.com"));
    hostRule.setPathMatcher(pathMatcher);

    lb.setHostRules(List.of(hostRule));

    GoogleRegionalExternalHttpLoadBalancer.RegionalExternalHttpLbView view = lb.getView();

    assertThat(view.getDefaultService()).isNotNull();
    assertThat(view.getDefaultService().getName()).isEqualTo("default-backend");
    assertThat(view.getHostRules()).hasSize(1);
    assertThat(view.getHostRules().get(0).getPathMatcher()).isNotNull();
    assertThat(view.getHostRules().get(0).getPathMatcher().getPathRules()).hasSize(1);
  }

  @Test
  void testCertificateMapAndSslCertificate() {
    GoogleRegionalExternalHttpLoadBalancer lb = new GoogleRegionalExternalHttpLoadBalancer();

    // Test with regular SSL certificate
    lb.setCertificate("my-ssl-cert");
    assertThat(lb.getCertificate()).isEqualTo("my-ssl-cert");
    assertThat(lb.getCertificateMap()).isNull();

    // Test with certificate map (Certificate Manager)
    lb.setCertificate(null);
    lb.setCertificateMap("my-cert-map");
    assertThat(lb.getCertificate()).isNull();
    assertThat(lb.getCertificateMap()).isEqualTo("my-cert-map");
  }

  @Test
  void testNetworkAndSubnet() {
    GoogleRegionalExternalHttpLoadBalancer lb = new GoogleRegionalExternalHttpLoadBalancer();
    lb.setNetwork("my-vpc");
    lb.setSubnet("my-subnet");

    assertThat(lb.getNetwork()).isEqualTo("my-vpc");
    assertThat(lb.getSubnet()).isEqualTo("my-subnet");

    GoogleRegionalExternalHttpLoadBalancer.RegionalExternalHttpLbView view = lb.getView();
    assertThat(view.getNetwork()).isEqualTo("my-vpc");
    assertThat(view.getSubnet()).isEqualTo("my-subnet");
  }
}
