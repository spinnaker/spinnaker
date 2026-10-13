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

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeRequest;
import com.google.api.services.compute.model.*;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spinnaker.clouddriver.google.batch.GoogleBatchRequest;
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck;
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleInternalHttpLoadBalancer;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancer;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleSessionAffinity;
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class GoogleInternalHttpLoadBalancerCachingAgentTest {
  private static final String PROJECT = "test";
  private static final String REGION = "us-central1";

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
    assertThat(ghc.getRequestPath()).isNull();
    assertThat(ghc.getGrpcServiceName()).isEqualTo("com.example.HealthService");
    assertThat(ghc.getHealthCheckType()).isEqualTo(GoogleHealthCheck.HealthCheckType.GRPC);
  }

  @Test
  void handleHealthCheck_withGrpcHealthCheck_servingPort() throws Exception {
    // Given
    HealthCheck healthCheck = buildBaseHealthCheck("grpc-hc-serving-port", "us-central1");
    GRPCHealthCheck grpcHealthCheck = new GRPCHealthCheck();
    grpcHealthCheck.setPortSpecification("USE_SERVING_PORT");
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
    assertThat(ghc.getName()).isEqualTo("grpc-hc-serving-port");
    assertThat(ghc.getPort()).isNull();
    assertThat(ghc.getPortSpecification()).isEqualTo("USE_SERVING_PORT");
    assertThat(ghc.getRequestPath()).isNull();
    assertThat(ghc.getGrpcServiceName()).isEqualTo("com.example.HealthService");
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
  void isOwnedForwardingRule_rejectsOtherManagedSchemes() {
    GoogleInternalHttpLoadBalancerCachingAgent agent = createAgent(mock(Compute.class));
    ForwardingRule internalManagedRule =
        new ForwardingRule()
            .setLoadBalancingScheme("INTERNAL_MANAGED")
            .setTarget("projects/test/regions/us-central1/targetHttpProxies/internal-proxy");
    ForwardingRule externalManagedRule =
        new ForwardingRule()
            .setLoadBalancingScheme("EXTERNAL_MANAGED")
            .setTarget("projects/test/regions/us-central1/targetHttpProxies/external-proxy");
    ForwardingRule sslProxyRule =
        new ForwardingRule()
            .setLoadBalancingScheme("INTERNAL_MANAGED")
            .setTarget("projects/test/regions/us-central1/targetSslProxies/ssl-proxy");

    assertThat(agent.isOwnedForwardingRule(internalManagedRule)).isTrue();
    assertThat(agent.isOwnedForwardingRule(externalManagedRule)).isFalse();
    assertThat(agent.isOwnedForwardingRule(sslProxyRule)).isFalse();
  }

  @Test
  void fullRefreshSkipsMalformedAndOtherProxySameSchemeForwardingRules() throws Exception {
    GoogleInternalHttpLoadBalancerCachingAgent agent = createAgent(mock(Compute.class));
    List<GoogleInternalHttpLoadBalancer> loadBalancers = new ArrayList<>();
    List<String> failedLoadBalancers = new ArrayList<>();
    GoogleBatchRequest targetProxyRequest = mock(GoogleBatchRequest.class);
    GoogleBatchRequest urlMapRequest = mock(GoogleBatchRequest.class);
    GoogleBatchRequest groupHealthRequest = mock(GoogleBatchRequest.class);
    GoogleInternalHttpLoadBalancerCachingAgent.ForwardingRuleCallbacks callbacks =
        agent
        .new ForwardingRuleCallbacks(
            loadBalancers,
            failedLoadBalancers,
            targetProxyRequest,
            urlMapRequest,
            groupHealthRequest,
            List.of(),
            List.of());
    ForwardingRule malformedRule =
        new ForwardingRule()
            .setName("malformed-lb")
            .setLoadBalancingScheme("INTERNAL_MANAGED")
            .setTarget("projects/test/regions/us-central1/targetUnknownProxies/unsupported-proxy");
    ForwardingRule managedTcpProxyRule =
        new ForwardingRule()
            .setName("managed-tcp-proxy")
            .setLoadBalancingScheme("INTERNAL_MANAGED")
            .setTarget("projects/test/regions/us-central1/targetTcpProxies/managed-tcp-proxy");

    callbacks
        .newForwardingRuleListCallback()
        .onSuccess(
            new ForwardingRuleList().setItems(List.of(malformedRule, managedTcpProxyRule)), null);

    assertThat(loadBalancers).isEmpty();
    assertThat(failedLoadBalancers).isEmpty();
    verifyNoInteractions(targetProxyRequest, urlMapRequest, groupHealthRequest);
  }

  @Test
  void onDemandDistinguishesMalformedSameSchemeRulesFromKnownProxyFamilies() {
    GoogleInternalHttpLoadBalancerCachingAgent agent = createAgent(mock(Compute.class));
    GoogleInternalHttpLoadBalancerCachingAgent.ForwardingRuleCallbacks callbacks =
        agent
        .new ForwardingRuleCallbacks(
            new ArrayList<>(),
            new ArrayList<>(),
            mock(GoogleBatchRequest.class),
            mock(GoogleBatchRequest.class),
            mock(GoogleBatchRequest.class),
            List.of(),
            List.of());
    ForwardingRule malformedRule =
        new ForwardingRule()
            .setName("malformed-lb")
            .setLoadBalancingScheme("INTERNAL_MANAGED")
            .setTarget("projects/test/regions/us-central1/targetUnknownProxies/unsupported-proxy");
    ForwardingRule managedTcpProxyRule =
        new ForwardingRule()
            .setName("managed-tcp-proxy")
            .setLoadBalancingScheme("INTERNAL_MANAGED")
            .setTarget("projects/test/regions/us-central1/targetTcpProxies/managed-tcp-proxy");

    assertThatThrownBy(
            () -> callbacks.newForwardingRuleSingletonCallback().onSuccess(malformedRule, null))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                callbacks.newForwardingRuleSingletonCallback().onSuccess(managedTcpProxyRule, null))
        .isInstanceOf(IllegalArgumentException.class);
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

  @Test
  void constructLoadBalancers_cachesRedirectingUrlMapAndTheRestOfTheRegion() throws IOException {
    HttpRedirectAction redirect = new HttpRedirectAction().setHttpsRedirect(true);
    PathMatcher pathMatcher =
        new PathMatcher()
            .setName("matcher")
            .setDefaultService(backendServiceUrl("backend-service"))
            .setPathRules(
                List.of(new PathRule().setPaths(List.of("/old/*")).setUrlRedirect(redirect)));
    UrlMap redirectMap =
        new UrlMap()
            .setName("redirect-map")
            .setDefaultUrlRedirect(redirect)
            .setPathMatchers(List.of(pathMatcher))
            .setHostRules(List.of(new HostRule().setHosts(List.of("*")).setPathMatcher("matcher")));
    UrlMap plainMap =
        new UrlMap().setName("plain-map").setDefaultService(backendServiceUrl("backend-service"));
    Compute compute = mock(Compute.class);
    stubRegion(
        compute,
        Map.of("redirect-lb", redirectMap, "plain-lb", plainMap),
        List.of(new BackendService().setName("backend-service").setSessionAffinity("CLIENT_IP")));

    Map<String, GoogleInternalHttpLoadBalancer> loadBalancers = constructLoadBalancers(compute);

    assertThat(loadBalancers).containsOnlyKeys("redirect-lb", "plain-lb");
    GoogleInternalHttpLoadBalancer loadBalancer = loadBalancers.get("redirect-lb");
    assertThat(loadBalancer.getDefaultService()).isNull();
    assertThat(loadBalancer.getHostRules().get(0).getPathMatcher().getPathRules())
        .singleElement()
        .satisfies(pathRule -> assertThat(pathRule.getBackendService()).isNull());
    assertThat(Utils.getBackendServicesFromInternalHttpLoadBalancerView(loadBalancer.getView()))
        .singleElement()
        .satisfies(
            service -> {
              assertThat(service.getName()).isEqualTo("backend-service");
              assertThat(service.getSessionAffinity()).isEqualTo(GoogleSessionAffinity.CLIENT_IP);
            });
  }

  @Test
  void constructLoadBalancers_keepsLoadBalancerWhoseBackendServiceOrHealthCheckIsMissing()
      throws IOException {
    UrlMap danglingMap =
        new UrlMap()
            .setName("dangling-map")
            .setDefaultService(backendServiceUrl("missing-service"))
            .setPathMatchers(
                List.of(
                    new PathMatcher()
                        .setName("matcher")
                        .setDefaultService(backendServiceUrl("backend-service"))))
            .setHostRules(List.of(new HostRule().setHosts(List.of("*")).setPathMatcher("matcher")));
    UrlMap plainMap =
        new UrlMap().setName("plain-map").setDefaultService(backendServiceUrl("backend-service"));
    Compute compute = mock(Compute.class);
    stubRegion(
        compute,
        Map.of("dangling-lb", danglingMap, "plain-lb", plainMap),
        List.of(
            new BackendService()
                .setName("backend-service")
                .setHealthChecks(
                    List.of(
                        "projects/" + PROJECT + "/regions/" + REGION + "/healthChecks/missing"))));

    Map<String, GoogleInternalHttpLoadBalancer> loadBalancers = constructLoadBalancers(compute);

    assertThat(loadBalancers).containsOnlyKeys("dangling-lb", "plain-lb");
    assertThat(
            Utils.getBackendServicesFromInternalHttpLoadBalancerView(
                loadBalancers.get("dangling-lb").getView()))
        .extracting(
            GoogleBackendService::getName,
            GoogleBackendService::getRegion,
            GoogleBackendService::getHealthCheck)
        .containsExactly(
            tuple("missing-service", null, null), tuple("backend-service", REGION, null));
  }

  private void invokeHandleHealthCheck(
      HealthCheck healthCheck, List<GoogleBackendService> googleBackendServices) {
    AbstractGoogleRegionalHttpLoadBalancerCachingAgent.handleHealthCheck(
        healthCheck, googleBackendServices);
  }

  private static Map<String, GoogleInternalHttpLoadBalancer> constructLoadBalancers(
      Compute compute) {
    return createAgent(compute).constructLoadBalancers().stream()
        .map(GoogleInternalHttpLoadBalancer.class::cast)
        .collect(toMap(GoogleLoadBalancer::getName, loadBalancer -> loadBalancer));
  }

  /** Stubs a region with one INTERNAL_MANAGED HTTP forwarding rule per URL map. */
  private static void stubRegion(
      Compute compute, Map<String, UrlMap> urlMapsByRuleName, List<BackendService> backendServices)
      throws IOException {
    Compute.RegionBackendServices regionBackendServices = mock(Compute.RegionBackendServices.class);
    Compute.RegionBackendServices.List listBackendServices =
        mock(Compute.RegionBackendServices.List.class);
    when(compute.regionBackendServices()).thenReturn(regionBackendServices);
    when(regionBackendServices.list(PROJECT, REGION)).thenReturn(listBackendServices);
    when(listBackendServices.execute())
        .thenReturn(new BackendServiceList().setItems(backendServices));

    Compute.RegionHealthChecks regionHealthChecks = mock(Compute.RegionHealthChecks.class);
    Compute.RegionHealthChecks.List listHealthChecks = mock(Compute.RegionHealthChecks.List.class);
    when(compute.regionHealthChecks()).thenReturn(regionHealthChecks);
    when(regionHealthChecks.list(PROJECT, REGION)).thenReturn(listHealthChecks);
    when(listHealthChecks.setPageToken(null)).thenReturn(listHealthChecks);
    when(listHealthChecks.execute()).thenReturn(new HealthCheckList());

    Compute.RegionTargetHttpProxies targetHttpProxies = mock(Compute.RegionTargetHttpProxies.class);
    Compute.RegionUrlMaps regionUrlMaps = mock(Compute.RegionUrlMaps.class);
    when(compute.regionTargetHttpProxies()).thenReturn(targetHttpProxies);
    when(compute.regionUrlMaps()).thenReturn(regionUrlMaps);
    List<ForwardingRule> forwardingRules = new ArrayList<>();
    for (Map.Entry<String, UrlMap> entry : urlMapsByRuleName.entrySet()) {
      String proxyName = entry.getKey() + "-proxy";
      String urlMapName = entry.getValue().getName();
      forwardingRules.add(
          new ForwardingRule()
              .setName(entry.getKey())
              .setRegion("projects/" + PROJECT + "/regions/" + REGION)
              .setLoadBalancingScheme("INTERNAL_MANAGED")
              .setTarget(
                  "projects/" + PROJECT + "/regions/" + REGION + "/targetHttpProxies/" + proxyName)
              .setPortRange("80-80"));
      Compute.RegionTargetHttpProxies.Get getProxy =
          mock(Compute.RegionTargetHttpProxies.Get.class);
      when(targetHttpProxies.get(PROJECT, REGION, proxyName)).thenReturn(getProxy);
      when(getProxy.execute())
          .thenReturn(
              new TargetHttpProxy()
                  .setUrlMap(
                      "projects/" + PROJECT + "/regions/" + REGION + "/urlMaps/" + urlMapName));
      Compute.RegionUrlMaps.Get getUrlMap = mock(Compute.RegionUrlMaps.Get.class);
      when(regionUrlMaps.get(PROJECT, REGION, urlMapName)).thenReturn(getUrlMap);
      when(getUrlMap.execute()).thenReturn(entry.getValue());
    }

    Compute.ForwardingRules computeForwardingRules = mock(Compute.ForwardingRules.class);
    Compute.ForwardingRules.List listForwardingRules = mock(Compute.ForwardingRules.List.class);
    when(compute.forwardingRules()).thenReturn(computeForwardingRules);
    when(computeForwardingRules.list(PROJECT, REGION)).thenReturn(listForwardingRules);
    when(listForwardingRules.setPageToken(null)).thenReturn(listForwardingRules);
    when(listForwardingRules.execute())
        .thenReturn(new ForwardingRuleList().setItems(forwardingRules));
  }

  private static String backendServiceUrl(String name) {
    return "projects/" + PROJECT + "/regions/" + REGION + "/backendServices/" + name;
  }

  private static GoogleInternalHttpLoadBalancerCachingAgent createAgent(Compute compute) {
    GoogleNamedAccountCredentials credentials =
        new GoogleNamedAccountCredentials.Builder()
            .name("auto")
            .project(PROJECT)
            .compute(compute)
            .credentials(mock(GoogleCredentials.class))
            .build();
    return new TestGoogleInternalHttpLoadBalancerCachingAgent(credentials);
  }

  /** Runs each Compute request and its batch callbacks inline, without the HTTP batch transport. */
  private static class TestGoogleInternalHttpLoadBalancerCachingAgent
      extends GoogleInternalHttpLoadBalancerCachingAgent {
    TestGoogleInternalHttpLoadBalancerCachingAgent(GoogleNamedAccountCredentials credentials) {
      super("clouddriver", credentials, new ObjectMapper(), new DefaultRegistry(), REGION);
    }

    @Override
    public <T> T timeExecute(AbstractGoogleClientRequest<T> request, String api, String... tags)
        throws IOException {
      return request.execute();
    }

    @Override
    public GoogleBatchRequest buildGoogleBatchRequest() {
      return new FakeGoogleBatchRequest();
    }

    @Override
    public Object executeIfRequestsAreQueued(
        GoogleBatchRequest googleBatchRequest, String instrumentationContext) {
      ((FakeGoogleBatchRequest) googleBatchRequest).executeQueued();
      return null;
    }
  }

  private static class FakeGoogleBatchRequest extends GoogleBatchRequest {
    private final List<ComputeRequest> requests = new ArrayList<>();
    private final List<JsonBatchCallback> callbacks = new ArrayList<>();

    FakeGoogleBatchRequest() {
      super(mock(Compute.class), "clouddriver");
    }

    @Override
    public void queue(ComputeRequest request, JsonBatchCallback callback) {
      requests.add(request);
      callbacks.add(callback);
    }

    @Override
    public Integer size() {
      return requests.size();
    }

    @SuppressWarnings("unchecked")
    void executeQueued() {
      for (int i = 0; i < requests.size(); i++) {
        try {
          callbacks.get(i).onSuccess(requests.get(i).execute(), new HttpHeaders());
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }
  }
}
