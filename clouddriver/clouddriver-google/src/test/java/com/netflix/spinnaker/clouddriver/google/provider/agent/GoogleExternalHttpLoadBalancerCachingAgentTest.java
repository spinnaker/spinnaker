/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.google.provider.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeRequest;
import com.google.api.services.compute.model.Backend;
import com.google.api.services.compute.model.BackendService;
import com.google.api.services.compute.model.BackendServiceGroupHealth;
import com.google.api.services.compute.model.BackendServiceList;
import com.google.api.services.compute.model.ForwardingRule;
import com.google.api.services.compute.model.ForwardingRuleList;
import com.google.api.services.compute.model.HealthCheckList;
import com.google.api.services.compute.model.HostRule;
import com.google.api.services.compute.model.PathMatcher;
import com.google.api.services.compute.model.PathRule;
import com.google.api.services.compute.model.ResourceGroupReference;
import com.google.api.services.compute.model.TargetHttpProxy;
import com.google.api.services.compute.model.TargetHttpsProxy;
import com.google.api.services.compute.model.UrlMap;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spinnaker.clouddriver.google.batch.GoogleBatchRequest;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleExternalHttpLoadBalancer;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancer;
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

public class GoogleExternalHttpLoadBalancerCachingAgentTest {
  private static final String ACCOUNT = "auto";
  private static final String PROJECT = "my-project";
  private static final String REGION = "us-central1";

  @Test
  void isExternalManagedHttpForwardingRule_acceptsOnlyExternalManagedHttpProxies() {
    ForwardingRule externalManagedHttp =
        new ForwardingRule()
            .setLoadBalancingScheme("EXTERNAL_MANAGED")
            .setTarget("projects/test/regions/us-central1/targetHttpProxies/external-proxy");
    ForwardingRule externalManagedHttps =
        new ForwardingRule()
            .setLoadBalancingScheme("EXTERNAL_MANAGED")
            .setTarget("projects/test/regions/us-central1/targetHttpsProxies/external-proxy");
    ForwardingRule internalManagedHttp =
        new ForwardingRule()
            .setLoadBalancingScheme("INTERNAL_MANAGED")
            .setTarget("projects/test/regions/us-central1/targetHttpProxies/internal-proxy");
    ForwardingRule externalManagedSsl =
        new ForwardingRule()
            .setLoadBalancingScheme("EXTERNAL_MANAGED")
            .setTarget("projects/test/regions/us-central1/targetSslProxies/ssl-proxy");
    ForwardingRule missingTarget = new ForwardingRule().setLoadBalancingScheme("EXTERNAL_MANAGED");

    assertThat(
            GoogleExternalHttpLoadBalancerCachingAgent.isExternalManagedHttpForwardingRule(
                externalManagedHttp))
        .isTrue();
    assertThat(
            GoogleExternalHttpLoadBalancerCachingAgent.isExternalManagedHttpForwardingRule(
                externalManagedHttps))
        .isTrue();
    assertThat(
            GoogleExternalHttpLoadBalancerCachingAgent.isExternalManagedHttpForwardingRule(
                internalManagedHttp))
        .isFalse();
    assertThat(
            GoogleExternalHttpLoadBalancerCachingAgent.isExternalManagedHttpForwardingRule(
                externalManagedSsl))
        .isFalse();
    assertThat(
            GoogleExternalHttpLoadBalancerCachingAgent.isExternalManagedHttpForwardingRule(
                missingTarget))
        .isFalse();
  }

  @Test
  void groupHealthCallback_toleratesBackendHealthFailures() {
    GoogleExternalHttpLoadBalancerCachingAgent agent = createAgent(mock(Compute.class));

    assertDoesNotThrow(
        () ->
            agent.new GroupHealthCallback("backend-service")
                .onFailure(googleError(500, "boom"), null));
  }

  @Test
  void forwardingRuleCallbacks_ignoreOnlyOnDemandNotFoundFailures() {
    GoogleExternalHttpLoadBalancerCachingAgent agent = createAgent(mock(Compute.class));
    GoogleExternalHttpLoadBalancerCachingAgent.ForwardingRuleCallbacks callbacks =
        agent
        .new ForwardingRuleCallbacks(
            new ArrayList<>(),
            new ArrayList<>(),
            mock(GoogleBatchRequest.class),
            mock(GoogleBatchRequest.class),
            mock(GoogleBatchRequest.class),
            Collections.emptyList(),
            Collections.emptyList());

    assertDoesNotThrow(
        () ->
            callbacks
                .newForwardingRuleSingletonCallback()
                .onFailure(googleError(404, "missing"), null));
    assertThatThrownBy(
            () ->
                callbacks
                    .newForwardingRuleSingletonCallback()
                    .onFailure(googleError(500, "boom"), null))
        .isInstanceOf(IOException.class);
    assertThatThrownBy(
            () ->
                callbacks.newForwardingRuleListCallback().onFailure(googleError(500, "boom"), null))
        .isInstanceOf(IOException.class);
  }

  @Test
  void constructLoadBalancers_filtersFailedTargetProxyAndKeepsOtherLoadBalancers()
      throws IOException {
    Compute compute = mock(Compute.class);
    configureSharedRegionalData(compute);
    Compute.ForwardingRules forwardingRules = mock(Compute.ForwardingRules.class);
    Compute.ForwardingRules.List listForwardingRules = mock(Compute.ForwardingRules.List.class);
    when(compute.forwardingRules()).thenReturn(forwardingRules);
    when(forwardingRules.list(PROJECT, REGION)).thenReturn(listForwardingRules);
    when(listForwardingRules.setPageToken(null)).thenReturn(listForwardingRules);
    when(listForwardingRules.execute())
        .thenReturn(
            new ForwardingRuleList()
                .setItems(
                    List.of(
                        buildHttpRule("bad-lb", "bad-proxy"),
                        buildHttpRule("good-lb", "good-proxy"))));

    Compute.RegionTargetHttpProxies targetHttpProxies = mock(Compute.RegionTargetHttpProxies.class);
    Compute.RegionTargetHttpProxies.Get getBadProxy =
        mock(Compute.RegionTargetHttpProxies.Get.class);
    Compute.RegionTargetHttpProxies.Get getGoodProxy =
        mock(Compute.RegionTargetHttpProxies.Get.class);
    when(compute.regionTargetHttpProxies()).thenReturn(targetHttpProxies);
    when(targetHttpProxies.get(PROJECT, REGION, "bad-proxy")).thenReturn(getBadProxy);
    when(targetHttpProxies.get(PROJECT, REGION, "good-proxy")).thenReturn(getGoodProxy);
    when(getBadProxy.execute()).thenThrow(new IOException("transient proxy failure"));
    when(getGoodProxy.execute()).thenReturn(new TargetHttpProxy().setUrlMap(urlMapUrl("good-map")));

    Compute.RegionUrlMaps regionUrlMaps = mock(Compute.RegionUrlMaps.class);
    Compute.RegionUrlMaps.Get getGoodMap = mock(Compute.RegionUrlMaps.Get.class);
    when(compute.regionUrlMaps()).thenReturn(regionUrlMaps);
    when(regionUrlMaps.get(PROJECT, REGION, "good-map")).thenReturn(getGoodMap);
    when(getGoodMap.execute())
        .thenReturn(
            new UrlMap()
                .setName("good-map")
                .setDefaultService(backendServiceUrl("backend-service")));

    GoogleExternalHttpLoadBalancerCachingAgent agent = createAgent(compute);

    List<GoogleLoadBalancer> loadBalancers = agent.constructLoadBalancers();

    assertThat(loadBalancers).hasSize(1);
    assertThat(loadBalancers.get(0).getName()).isEqualTo("good-lb");
  }

  @Test
  void constructLoadBalancers_readsOnDemandHttpLoadBalancerGraph() throws IOException {
    Compute compute = mock(Compute.class);
    configureSharedRegionalData(compute);
    Compute.ForwardingRules forwardingRules = mock(Compute.ForwardingRules.class);
    Compute.ForwardingRules.Get getForwardingRule = mock(Compute.ForwardingRules.Get.class);
    when(compute.forwardingRules()).thenReturn(forwardingRules);
    when(forwardingRules.get(PROJECT, REGION, "good-lb")).thenReturn(getForwardingRule);
    when(getForwardingRule.execute()).thenReturn(buildHttpRule("good-lb", "good-proxy"));

    Compute.RegionTargetHttpProxies targetHttpProxies = mock(Compute.RegionTargetHttpProxies.class);
    Compute.RegionTargetHttpProxies.Get getGoodProxy =
        mock(Compute.RegionTargetHttpProxies.Get.class);
    when(compute.regionTargetHttpProxies()).thenReturn(targetHttpProxies);
    when(targetHttpProxies.get(PROJECT, REGION, "good-proxy")).thenReturn(getGoodProxy);
    when(getGoodProxy.execute()).thenReturn(new TargetHttpProxy().setUrlMap(urlMapUrl("good-map")));

    Compute.RegionUrlMaps regionUrlMaps = mock(Compute.RegionUrlMaps.class);
    Compute.RegionUrlMaps.Get getGoodMap = mock(Compute.RegionUrlMaps.Get.class);
    when(compute.regionUrlMaps()).thenReturn(regionUrlMaps);
    when(regionUrlMaps.get(PROJECT, REGION, "good-map")).thenReturn(getGoodMap);
    when(getGoodMap.execute())
        .thenReturn(
            new UrlMap()
                .setName("good-map")
                .setDefaultService(backendServiceUrl("backend-service")));

    GoogleExternalHttpLoadBalancerCachingAgent agent = createAgent(compute);

    List<GoogleLoadBalancer> loadBalancers = agent.constructLoadBalancers("good-lb");

    assertThat(loadBalancers).hasSize(1);
    assertThat(loadBalancers.get(0).getName()).isEqualTo("good-lb");
    verify(forwardingRules).get(PROJECT, REGION, "good-lb");
  }

  @Test
  void constructLoadBalancers_rejectsWrongSchemeOnDemandForwardingRule() throws IOException {
    Compute compute = mock(Compute.class);
    configureSharedRegionalData(compute);
    Compute.ForwardingRules forwardingRules = mock(Compute.ForwardingRules.class);
    Compute.ForwardingRules.Get getForwardingRule = mock(Compute.ForwardingRules.Get.class);
    when(compute.forwardingRules()).thenReturn(forwardingRules);
    when(forwardingRules.get(PROJECT, REGION, "internal-lb")).thenReturn(getForwardingRule);
    when(getForwardingRule.execute())
        .thenReturn(
            buildHttpRule("internal-lb", "internal-proxy")
                .setLoadBalancingScheme("INTERNAL_MANAGED"));

    GoogleExternalHttpLoadBalancerCachingAgent agent = createAgent(compute);

    assertThatThrownBy(() -> agent.constructLoadBalancers("internal-lb"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructLoadBalancers_readsOnDemandHttpsLoadBalancerGraphAndCertificate()
      throws IOException {
    Compute compute = mock(Compute.class);
    configureSharedRegionalData(compute);
    Compute.ForwardingRules forwardingRules = mock(Compute.ForwardingRules.class);
    Compute.ForwardingRules.Get getForwardingRule = mock(Compute.ForwardingRules.Get.class);
    when(compute.forwardingRules()).thenReturn(forwardingRules);
    when(forwardingRules.get(PROJECT, REGION, "https-lb")).thenReturn(getForwardingRule);
    when(getForwardingRule.execute()).thenReturn(buildHttpsRule("https-lb", "https-proxy"));

    String certificateManagerCertificate =
        "//certificatemanager.googleapis.com/projects/"
            + PROJECT
            + "/locations/"
            + REGION
            + "/certificates/shared-name";
    Compute.RegionTargetHttpsProxies targetHttpsProxies =
        mock(Compute.RegionTargetHttpsProxies.class);
    Compute.RegionTargetHttpsProxies.Get getHttpsProxy =
        mock(Compute.RegionTargetHttpsProxies.Get.class);
    when(compute.regionTargetHttpsProxies()).thenReturn(targetHttpsProxies);
    when(targetHttpsProxies.get(PROJECT, REGION, "https-proxy")).thenReturn(getHttpsProxy);
    when(getHttpsProxy.execute())
        .thenReturn(
            new TargetHttpsProxy()
                .setSslCertificates(List.of(certificateManagerCertificate))
                .setUrlMap(urlMapUrl("https-map")));

    Compute.RegionUrlMaps regionUrlMaps = mock(Compute.RegionUrlMaps.class);
    Compute.RegionUrlMaps.Get getHttpsMap = mock(Compute.RegionUrlMaps.Get.class);
    when(compute.regionUrlMaps()).thenReturn(regionUrlMaps);
    when(regionUrlMaps.get(PROJECT, REGION, "https-map")).thenReturn(getHttpsMap);
    when(getHttpsMap.execute())
        .thenReturn(
            new UrlMap()
                .setName("https-map")
                .setDefaultService(backendServiceUrl("backend-service")));

    GoogleExternalHttpLoadBalancerCachingAgent agent = createAgent(compute);

    List<GoogleLoadBalancer> loadBalancers = agent.constructLoadBalancers("https-lb");

    assertThat(loadBalancers).hasSize(1);
    assertThat(loadBalancers.get(0)).isInstanceOf(GoogleExternalHttpLoadBalancer.class);
    assertThat(((GoogleExternalHttpLoadBalancer) loadBalancers.get(0)).getCertificate())
        .isEqualTo(certificateManagerCertificate);
  }

  @Test
  void constructLoadBalancers_dedupesBackendHealthRequestsForRepeatedBackendService()
      throws IOException {
    Compute compute = mock(Compute.class);
    String groupUrl =
        "https://compute.googleapis.com/compute/v1/projects/"
            + PROJECT
            + "/zones/us-central1-a/instanceGroups/server-group";
    Compute.RegionBackendServices regionBackendServices = mock(Compute.RegionBackendServices.class);
    Compute.RegionBackendServices.List listBackendServices =
        mock(Compute.RegionBackendServices.List.class);
    Compute.RegionBackendServices.GetHealth getHealth =
        mock(Compute.RegionBackendServices.GetHealth.class);
    when(compute.regionBackendServices()).thenReturn(regionBackendServices);
    when(regionBackendServices.list(PROJECT, REGION)).thenReturn(listBackendServices);
    when(listBackendServices.execute())
        .thenReturn(
            new BackendServiceList()
                .setItems(
                    List.of(
                        new BackendService()
                            .setName("backend-service")
                            .setSessionAffinity("NONE")
                            .setBackends(
                                List.of(
                                    new Backend()
                                        .setGroup(groupUrl)
                                        .setBalancingMode("UTILIZATION")))
                            .setHealthChecks(Collections.emptyList()))));
    when(regionBackendServices.getHealth(
            org.mockito.ArgumentMatchers.eq(PROJECT),
            org.mockito.ArgumentMatchers.eq(REGION),
            org.mockito.ArgumentMatchers.eq("backend-service"),
            org.mockito.ArgumentMatchers.any(ResourceGroupReference.class)))
        .thenReturn(getHealth);
    when(getHealth.execute()).thenReturn(new BackendServiceGroupHealth());
    Compute.RegionHealthChecks regionHealthChecks = mock(Compute.RegionHealthChecks.class);
    Compute.RegionHealthChecks.List listHealthChecks = mock(Compute.RegionHealthChecks.List.class);
    when(compute.regionHealthChecks()).thenReturn(regionHealthChecks);
    when(regionHealthChecks.list(PROJECT, REGION)).thenReturn(listHealthChecks);
    when(listHealthChecks.setPageToken(null)).thenReturn(listHealthChecks);
    when(listHealthChecks.execute())
        .thenReturn(new HealthCheckList().setItems(Collections.emptyList()));

    Compute.ForwardingRules forwardingRules = mock(Compute.ForwardingRules.class);
    Compute.ForwardingRules.Get getForwardingRule = mock(Compute.ForwardingRules.Get.class);
    when(compute.forwardingRules()).thenReturn(forwardingRules);
    when(forwardingRules.get(PROJECT, REGION, "good-lb")).thenReturn(getForwardingRule);
    when(getForwardingRule.execute()).thenReturn(buildHttpRule("good-lb", "good-proxy"));

    Compute.RegionTargetHttpProxies targetHttpProxies = mock(Compute.RegionTargetHttpProxies.class);
    Compute.RegionTargetHttpProxies.Get getGoodProxy =
        mock(Compute.RegionTargetHttpProxies.Get.class);
    when(compute.regionTargetHttpProxies()).thenReturn(targetHttpProxies);
    when(targetHttpProxies.get(PROJECT, REGION, "good-proxy")).thenReturn(getGoodProxy);
    when(getGoodProxy.execute()).thenReturn(new TargetHttpProxy().setUrlMap(urlMapUrl("good-map")));

    Compute.RegionUrlMaps regionUrlMaps = mock(Compute.RegionUrlMaps.class);
    Compute.RegionUrlMaps.Get getGoodMap = mock(Compute.RegionUrlMaps.Get.class);
    when(compute.regionUrlMaps()).thenReturn(regionUrlMaps);
    when(regionUrlMaps.get(PROJECT, REGION, "good-map")).thenReturn(getGoodMap);
    when(getGoodMap.execute()).thenReturn(repeatedBackendUrlMap("good-map", "backend-service"));

    GoogleExternalHttpLoadBalancerCachingAgent agent = createAgent(compute);

    assertThat(agent.constructLoadBalancers("good-lb")).hasSize(1);
    verify(regionBackendServices)
        .getHealth(
            org.mockito.ArgumentMatchers.eq(PROJECT),
            org.mockito.ArgumentMatchers.eq(REGION),
            org.mockito.ArgumentMatchers.eq("backend-service"),
            org.mockito.ArgumentMatchers.any(ResourceGroupReference.class));
  }

  @Test
  void getFirstSslCertificateForExternalManaged_preservesCertificateManagerResourceIdentity() {
    String certificateManagerCertificate =
        "//certificatemanager.googleapis.com/projects/"
            + PROJECT
            + "/locations/"
            + REGION
            + "/certificates/shared-name";
    String computeCertificate =
        "https://compute.googleapis.com/compute/v1/projects/"
            + PROJECT
            + "/regions/"
            + REGION
            + "/sslCertificates/shared-name";

    assertThat(
            GoogleExternalHttpLoadBalancerCachingAgent.getFirstSslCertificateForExternalManaged(
                new TargetHttpsProxy().setSslCertificates(new ArrayList<>())))
        .isNull();
    assertThat(
            GoogleExternalHttpLoadBalancerCachingAgent.getFirstSslCertificateForExternalManaged(
                new TargetHttpsProxy().setSslCertificates(List.of(certificateManagerCertificate))))
        .isEqualTo(certificateManagerCertificate);
    assertThat(
            GoogleExternalHttpLoadBalancerCachingAgent.getFirstSslCertificateForExternalManaged(
                new TargetHttpsProxy().setSslCertificates(List.of(computeCertificate))))
        .isEqualTo("shared-name");
  }

  private static GoogleExternalHttpLoadBalancerCachingAgent createAgent(Compute compute) {
    GoogleNamedAccountCredentials credentials =
        new GoogleNamedAccountCredentials.Builder()
            .name(ACCOUNT)
            .project(PROJECT)
            .compute(compute)
            .credentials(mock(GoogleCredentials.class))
            .build();
    return new TestGoogleExternalHttpLoadBalancerCachingAgent(
        "clouddriver", credentials, new ObjectMapper(), new DefaultRegistry(), REGION);
  }

  private static void configureSharedRegionalData(Compute compute) throws IOException {
    Compute.RegionBackendServices regionBackendServices = mock(Compute.RegionBackendServices.class);
    Compute.RegionBackendServices.List listBackendServices =
        mock(Compute.RegionBackendServices.List.class);
    when(compute.regionBackendServices()).thenReturn(regionBackendServices);
    when(regionBackendServices.list(PROJECT, REGION)).thenReturn(listBackendServices);
    when(listBackendServices.execute())
        .thenReturn(
            new BackendServiceList()
                .setItems(
                    List.of(
                        new BackendService()
                            .setName("backend-service")
                            .setSessionAffinity("NONE")
                            .setBackends(Collections.emptyList())
                            .setHealthChecks(Collections.emptyList()))));

    Compute.RegionHealthChecks regionHealthChecks = mock(Compute.RegionHealthChecks.class);
    Compute.RegionHealthChecks.List listHealthChecks = mock(Compute.RegionHealthChecks.List.class);
    when(compute.regionHealthChecks()).thenReturn(regionHealthChecks);
    when(regionHealthChecks.list(PROJECT, REGION)).thenReturn(listHealthChecks);
    when(listHealthChecks.setPageToken(null)).thenReturn(listHealthChecks);
    when(listHealthChecks.execute())
        .thenReturn(new HealthCheckList().setItems(Collections.emptyList()));
  }

  private static ForwardingRule buildHttpRule(String name, String targetProxy) {
    return new ForwardingRule()
        .setName(name)
        .setRegion("projects/" + PROJECT + "/regions/" + REGION)
        .setLoadBalancingScheme("EXTERNAL_MANAGED")
        .setTarget(
            "projects/" + PROJECT + "/regions/" + REGION + "/targetHttpProxies/" + targetProxy)
        .setIPAddress("1.2.3.4")
        .setIPProtocol("TCP")
        .setPortRange("80-80");
  }

  private static ForwardingRule buildHttpsRule(String name, String targetProxy) {
    return new ForwardingRule()
        .setName(name)
        .setRegion("projects/" + PROJECT + "/regions/" + REGION)
        .setLoadBalancingScheme("EXTERNAL_MANAGED")
        .setTarget(
            "projects/" + PROJECT + "/regions/" + REGION + "/targetHttpsProxies/" + targetProxy)
        .setIPAddress("1.2.3.4")
        .setIPProtocol("TCP")
        .setPortRange("443-443");
  }

  private static UrlMap repeatedBackendUrlMap(String name, String backendService) {
    PathRule pathRule =
        new PathRule().setPaths(List.of("/app/*")).setService(backendServiceUrl(backendService));
    PathMatcher pathMatcher =
        new PathMatcher()
            .setName("matcher")
            .setDefaultService(backendServiceUrl(backendService))
            .setPathRules(List.of(pathRule));
    HostRule hostRule = new HostRule().setHosts(List.of("*")).setPathMatcher("matcher");
    return new UrlMap()
        .setName(name)
        .setDefaultService(backendServiceUrl(backendService))
        .setPathMatchers(List.of(pathMatcher))
        .setHostRules(List.of(hostRule));
  }

  private static String urlMapUrl(String name) {
    return "projects/" + PROJECT + "/regions/" + REGION + "/urlMaps/" + name;
  }

  private static String backendServiceUrl(String name) {
    return "projects/" + PROJECT + "/regions/" + REGION + "/backendServices/" + name;
  }

  private static class TestGoogleExternalHttpLoadBalancerCachingAgent
      extends GoogleExternalHttpLoadBalancerCachingAgent {
    TestGoogleExternalHttpLoadBalancerCachingAgent(
        String clouddriverUserAgentApplicationName,
        GoogleNamedAccountCredentials credentials,
        ObjectMapper objectMapper,
        DefaultRegistry registry,
        String region) {
      super(clouddriverUserAgentApplicationName, credentials, objectMapper, registry, region);
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
      if (googleBatchRequest.size() > 0) {
        ((FakeGoogleBatchRequest) googleBatchRequest).executeQueued();
      }
      return null;
    }
  }

  private static class FakeGoogleBatchRequest extends GoogleBatchRequest {
    private final List<QueuedRequest> queuedRequests = new CopyOnWriteArrayList<>();

    FakeGoogleBatchRequest() {
      super(mock(Compute.class), "clouddriver");
    }

    @Override
    public void queue(ComputeRequest request, JsonBatchCallback callback) {
      queuedRequests.add(new QueuedRequest(request, callback));
    }

    @Override
    public Integer size() {
      return queuedRequests.size();
    }

    void executeQueued() {
      for (QueuedRequest queuedRequest : queuedRequests) {
        try {
          Object response = queuedRequest.request.execute();
          queuedRequest.callback.onSuccess(response, new HttpHeaders());
        } catch (GoogleJsonResponseException e) {
          try {
            queuedRequest.callback.onFailure(e.getDetails(), e.getHeaders());
          } catch (IOException callbackException) {
            throw new RuntimeException(callbackException);
          }
        } catch (IOException e) {
          try {
            queuedRequest.callback.onFailure(googleError(500, e.getMessage()), new HttpHeaders());
          } catch (IOException callbackException) {
            throw new RuntimeException(callbackException);
          }
        }
      }
    }

    private static class QueuedRequest {
      private final ComputeRequest request;
      private final JsonBatchCallback callback;

      private QueuedRequest(ComputeRequest request, JsonBatchCallback callback) {
        this.request = request;
        this.callback = callback;
      }
    }
  }

  private static GoogleJsonError googleError(int code, String message) {
    GoogleJsonError error = new GoogleJsonError();
    error.setCode(code);
    error.setMessage(message);
    return error;
  }
}
