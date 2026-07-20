/*
 * Copyright 2025 Wise, PLC.
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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.streaming;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.google.common.collect.ImmutableList;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import com.netflix.spinnaker.cats.agent.LongRunningAgentExecution;
import com.netflix.spinnaker.cats.agent.NoOpStartupConcurrencyControl;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.cache.NamedCacheFactory;
import com.netflix.spinnaker.cats.cache.WriteableCache;
import com.netflix.spinnaker.cats.mem.InMemoryNamedCacheFactory;
import com.netflix.spinnaker.cats.provider.DefaultProviderRegistry;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesStreamingCachingProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

public class KubernetesStreamingCachingAgentExecutionIntTest
    extends BaseKubernetesStreamingCachingAgentIntTest {

  @Test
  @DisplayName("loads all existing resources with 'list' when starts")
  void testInitialLoad() throws InterruptedException {
    mockKubeapiList("/api/v1/pods", "initialList/pods.json");
    mockKubeapiList("/apis/apps/v1/replicasets", "initialList/replicasets.json");

    runCachingAgentAndExpect(
        Map.of(
            POD_KIND,
                Set.of(
                    "kubernetes.v2:infrastructure:pod:my-account:test-namespace:my-pod-1",
                    "kubernetes.v2:infrastructure:pod:my-account:test-namespace:my-pod-2"),
            REPLICA_SET_KIND,
                Set.of(
                    "kubernetes.v2:infrastructure:replicaSet:my-account:test-namespace:my-replicaset")));
  }

  @Test
  @DisplayName("loads all existing resources with paginated 'list' when starts")
  void testInitialLoadPaginated() throws InterruptedException {
    mockKubeapiList("/api/v1/pods", null, null, "initialList/paginated-pods-1.json");
    mockKubeapiList(
        "/api/v1/pods", null, "some-arbitrary-tag", "initialList/paginated-pods-2.json");
    mockKubeapiList("/apis/apps/v1/replicasets", "initialList/replicasets.json");

    runCachingAgentAndExpect(
        Map.of(
            POD_KIND,
            Set.of(
                "kubernetes.v2:infrastructure:pod:my-account:test-namespace:my-pod-1",
                "kubernetes.v2:infrastructure:pod:my-account:test-namespace:my-pod-2"),
            REPLICA_SET_KIND,
            Set.of(
                "kubernetes.v2:infrastructure:replicaSet:my-account:test-namespace:my-replicaset")),
        1);
  }

  @Test
  @DisplayName("paginated 'list' returns resource expired. retry should help")
  void testPaginatedListErrorResponse() throws InterruptedException {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v1/pods"))
            .withQueryParam("watch", or(equalTo("false"), absent()))
            .withQueryParam("resourceVersion", or(equalTo("0"), absent()))
            .withQueryParam("limit", matching(".*"))
            .inScenario("list returns resource expired")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("return resource expired")
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("initialList/paginated-pods-1.json")));

    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v1/pods"))
            .withQueryParam("watch", or(equalTo("false"), absent()))
            .withQueryParam("resourceVersion", or(equalTo("0"), absent()))
            .withQueryParam("limit", matching(".*"))
            .inScenario("list returns resource expired")
            .whenScenarioStateIs("return resource expired")
            .willSetStateTo("resource expired returned")
            .willReturn(
                aResponse().withHeader("Content-Type", "application/json").withStatus(410)));

    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v1/pods"))
            .withQueryParam("watch", or(equalTo("false"), absent()))
            .withQueryParam("resourceVersion", or(equalTo("0"), absent()))
            .withQueryParam("limit", matching(".*"))
            .inScenario("list returns resource expired")
            .whenScenarioStateIs("resource expired returned")
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("initialList/paginated-pods-1.json")));

    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v1/pods"))
            .withQueryParam("watch", or(equalTo("false"), absent()))
            .withQueryParam("resourceVersion", or(equalTo("0"), absent()))
            .withQueryParam("limit", matching(".*"))
            .withQueryParam("continue", equalTo("some-arbitrary-tag"))
            .inScenario("list returns resource expired")
            .whenScenarioStateIs("resource expired returned")
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("initialList/paginated-pods-2.json")));

    runCachingAgentAndExpect(
        Map.of(
            POD_KIND,
            Set.of(
                "kubernetes.v2:infrastructure:pod:my-account:test-namespace:my-pod-1",
                "kubernetes.v2:infrastructure:pod:my-account:test-namespace:my-pod-2")),
        1);
  }

  @Test
  @DisplayName("saves relationships on initial load")
  void testSaveRelationshipsOnInitialLoad() throws InterruptedException {
    mockKubeapiList("/api/v1/pods", "initialList/pods.json");
    mockKubeapiList("/apis/apps/v1/replicasets", "initialList/replicasets.json");

    runCachingAgentAndAssert(
        (cache) -> {
          CacheData pod =
              cache.get(
                  POD_KIND, "kubernetes.v2:infrastructure:pod:my-account:test-namespace:my-pod-1");
          assertThat(pod).isNotNull();
          assertThat(pod.getRelationships())
              .containsEntry(
                  REPLICA_SET_KIND,
                  Set.of(
                      "kubernetes.v2:infrastructure:replicaSet:my-account:test-namespace:my-replicaset"));
        },
        0);
  }

  @Test
  @DisplayName("saves relationships on 'watch' events")
  void testSaveRelationshipsOnWatchEvents() throws InterruptedException {
    mockKubeapiList("/api/v1/pods", "initialList/pods.json");
    mockKubeapiList("/apis/apps/v1/replicasets", "initialList/replicasets.json");

    // add new pod. send via watch
    mockKubeapiWatch("/api/v1/pods", WatchEvent.added("add/pod.json"));

    runCachingAgentAndAssert(
        (cache) -> {
          CacheData pod =
              cache.get(
                  POD_KIND, "kubernetes.v2:infrastructure:pod:my-account:test-namespace:my-pod-3");
          assertThat(pod).isNotNull();
          assertThat(pod.getRelationships())
              .containsEntry(
                  REPLICA_SET_KIND,
                  Set.of(
                      "kubernetes.v2:infrastructure:replicaSet:my-account:test-namespace:my-replicaset"));
        },
        0);
  }

  @Test
  @DisplayName("insert resources when 'watch' receives 'added' event")
  void testListAndWatchAdd() throws InterruptedException {
    // add new pod. send via watch
    mockKubeapiWatch("/api/v1/pods", WatchEvent.added("add/pod.json"));

    runCachingAgentAndExpect(
        Map.of(
            POD_KIND,
            Set.of("kubernetes.v2:infrastructure:pod:my-account:test-namespace:my-pod-3")));
  }

  @Test
  @DisplayName("update resources when 'watch' receives 'modified' event")
  void testListAndWatchUpdate() throws InterruptedException {
    mockKubeapiList("/api/v1/pods", "initialList/pods.json");
    mockKubeapiList("/apis/apps/v1/replicasets", "initialList/replicasets.json");

    // update container image. send via watch
    mockKubeapiWatch("/api/v1/pods", WatchEvent.modified("modify/pod.json"));

    runCachingAgentAndAssert(
        (cache) -> {
          CacheData pod =
              cache.get(
                  POD_KIND, "kubernetes.v2:infrastructure:pod:my-account:test-namespace:my-pod-1");
          assertThat(pod).isNotNull();
          assertThat(pod.getAttributes())
              .extracting("manifest.spec.containers")
              .asList()
              .first()
              .extracting("image")
              .isEqualTo("my-pod-container-image:v2");
        },
        0);
  }

  @Test
  @DisplayName("delete resources when 'watch' receives 'deleted' event")
  void testListAndWatchDelete() throws InterruptedException {
    mockKubeapiList("/api/v1/pods", "initialList/pods.json");

    // delete pod. send via watch
    mockKubeapiWatch("/api/v1/pods", WatchEvent.deleted("delete/pod.json"));

    runCachingAgentAndExpect(
        Map.of(
            POD_KIND,
            Set.of("kubernetes.v2:infrastructure:pod:my-account:test-namespace:my-pod-2")));
  }

  @Test
  @DisplayName("serves multiple namespaces")
  void testMultipleNamespaces() throws InterruptedException {
    // 'list': replicasets in multiple namespaces
    mockKubeapiList("/apis/apps/v1/replicasets", "multiple-namespaces/list.json");

    // 'watch': pods in multiple namespaces
    mockKubeapiWatch(
        "/api/v1/pods",
        WatchEvent.added("multiple-namespaces/watch-pod1.json"),
        WatchEvent.added("multiple-namespaces/watch-pod2.json"));

    runCachingAgentAndExpect(
        Map.of(
            POD_KIND,
                Set.of(
                    "kubernetes.v2:infrastructure:pod:my-account:test-namespace:my-pod-1",
                    "kubernetes.v2:infrastructure:pod:my-account:test-namespace2:my-pod-2"),
            REPLICA_SET_KIND,
                Set.of(
                    "kubernetes.v2:infrastructure:replicaSet:my-account:test-namespace:my-replicaset",
                    "kubernetes.v2:infrastructure:replicaSet:my-account:test-namespace2:my-replicaset")));
  }

  @Test
  @DisplayName("ignores unknown namespaces")
  void testUnknownNamespaces() throws InterruptedException {
    mockKubeapiList("/apis/apps/v1/replicasets", "unknown-namespace/list.json");
    mockKubeapiWatch(
        "/api/v1/pods",
        WatchEvent.added("unknown-namespace/watch-pod1.json"),
        WatchEvent.added("unknown-namespace/watch-pod2.json"));

    runCachingAgentAndExpect(
        Map.of(
            POD_KIND, Set.of("kubernetes.v2:infrastructure:pod:my-account:test-namespace:my-pod-1"),
            REPLICA_SET_KIND,
                Set.of(
                    "kubernetes.v2:infrastructure:replicaSet:my-account:test-namespace:my-replicaset")));
  }

  @Test
  @DisplayName("delete stale resources after initial load")
  void testDeleteStaleResources() throws InterruptedException {
    // create pod-3 in cache
    ProviderCache cache = providerRegistry.getProviderCache(kubernetesProvider.getProviderName());
    cache.putCacheData(
        POD_KIND,
        new DefaultCacheData(
            "kubernetes.v2:infrastructure:pod:my-account:test-namespace:my-pod-3",
            Map.of("manifest", Map.of("metadata", Map.of("name", "my-pod-3"))),
            Map.of(
                REPLICA_SET_KIND,
                Set.of(
                    "kubernetes.v2:infrastructure:replicaSet:my-account:test-namespace:my-replicaset"))));

    assertThat(cache.getIdentifiers(POD_KIND))
        .containsExactly("kubernetes.v2:infrastructure:pod:my-account:test-namespace:my-pod-3");

    // contains only pod-1 and pod-2
    mockKubeapiList("/api/v1/pods", "initialList/pods.json");

    // run caching agent and wait for 'pod-3' to be deleted
    runCachingAgentAndExpect(
        Map.of(
            POD_KIND,
            Set.of(
                "kubernetes.v2:infrastructure:pod:my-account:test-namespace:my-pod-1",
                "kubernetes.v2:infrastructure:pod:my-account:test-namespace:my-pod-2")));
  }

  @ParameterizedTest(name = "error type: {0}")
  @DisplayName("'list' returns error. retry should help")
  @ValueSource(strings = {"partial response", "response with http error"})
  void testListErrorResponse(String errorType) throws InterruptedException {
    ResponseDefinitionBuilder errorResponse =
        errorType.equals("partial response")
            ? aResponse()
                .withHeader("Content-Type", "application/json;stream=watch")
                .withBody(WatchEvent.added("partial/pods.json").getContent())
            : aResponse().withHeader("Content-Type", "application/json").withStatus(500);

    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v1/pods"))
            .withQueryParam("watch", or(equalTo("false"), absent()))
            .inScenario("list partial response")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("invalid response returned")
            .willReturn(errorResponse));

    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v1/pods"))
            .withQueryParam("watch", or(equalTo("false"), absent()))
            .inScenario("list partial response")
            .whenScenarioStateIs("invalid response returned")
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("initialList/pods.json")));

    runCachingAgentAndExpect(
        Map.of(
            POD_KIND,
            Set.of(
                "kubernetes.v2:infrastructure:pod:my-account:test-namespace:my-pod-1",
                "kubernetes.v2:infrastructure:pod:my-account:test-namespace:my-pod-2")));
  }

  @ParameterizedTest(name = "error type: {0}")
  @DisplayName("'watch' returns error. retry should help")
  @ValueSource(strings = {"partial response", "response with http error"})
  void testWatchErrorResponse(String errorType) throws InterruptedException {
    // "list" returns pod-1 and pod-2 (valid)
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v1/pods"))
            .withQueryParam("watch", or(equalTo("false"), absent()))
            .inScenario("watch partial response")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("list returned")
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("initialList/pods.json")));

    ResponseDefinitionBuilder errorResponse =
        errorType.equals("partial response")
            ? aResponse()
                .withHeader("Content-Type", "application/json;stream=watch")
                .withBody(WatchEvent.added("partial/pod.json").getContent())
            : aResponse().withHeader("Content-Type", "application/json").withStatus(500);

    // "watch" returns partial response (invalid)
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v1/pods"))
            .withQueryParam("watch", equalTo("true"))
            .withQueryParam("resourceVersion", equalTo("1"))
            .inScenario("watch partial response")
            .whenScenarioStateIs("list returned")
            .willSetStateTo("invalid response returned")
            .willReturn(errorResponse));

    // retry "list" with the last valid resourceVersion (valid)
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v1/pods"))
            .withQueryParam("watch", or(equalTo("false"), absent()))
            .inScenario("watch partial response")
            .whenScenarioStateIs("invalid response returned")
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("partial/valid-pods.json")));

    runCachingAgentAndExpect(
        Map.of(
            POD_KIND,
            Set.of(
                "kubernetes.v2:infrastructure:pod:my-account:test-namespace:my-pod-1",
                "kubernetes.v2:infrastructure:pod:my-account:test-namespace:my-pod-2",
                "kubernetes.v2:infrastructure:pod:my-account:test-namespace:my-pod-3")));
  }

  @Test
  @DisplayName("'api discovery' returns error. retry should help")
  void testApiDiscoveryError() throws InterruptedException, IOException, ApiException {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api"))
            .inScenario("api discovery error")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("invalid response returned")
            .willReturn(aResponse().withStatus(500)));

    wireMockServer.stubFor(
        get(urlPathEqualTo("/api"))
            .inScenario("api discovery error")
            .whenScenarioStateIs("invalid response returned")
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("kubeapi/api.json")));

    mockKubeapiList("/apis/apps/v1/replicasets", "initialList/replicasets.json");

    runCachingAgentAndExpect(
        Map.of(
            REPLICA_SET_KIND,
            Set.of(
                "kubernetes.v2:infrastructure:replicaSet:my-account:test-namespace:my-replicaset")));
  }

  @Test
  @DisplayName("handler throws exception. skip broken batch and continue")
  void testHandlerException() throws InterruptedException {
    // create a mocked cache that throws exception when you save data the first time it is called.
    // then call the real method.
    InMemoryNamedCacheFactory cacheFactory = new InMemoryNamedCacheFactory();
    WriteableCache realCache = cacheFactory.getCache(kubernetesProvider.getProviderName());

    WriteableCache mockedCache = Mockito.spy(realCache);
    Mockito.doAnswer(
            invocation -> {
              // set state that we processed the first batch, and then throw exception
              wireMockServer.setScenarioState("broken batch", "first batch was skipped");
              throw new RuntimeException("test exception");
            })
        .doAnswer(InvocationOnMock::callRealMethod)
        .when(mockedCache)
        .mergeAll(anyString(), anyCollection());

    NamedCacheFactory mockedCacheFactory = Mockito.mock(NamedCacheFactory.class);
    Mockito.when(mockedCacheFactory.getCache(anyString())).thenReturn(mockedCache);

    providerRegistry =
        new DefaultProviderRegistry(ImmutableList.of(kubernetesProvider), mockedCacheFactory);

    // first batch, will fail
    mockKubeapiList("/api/v1/pods", "initialList/pods.json");

    // second batch, will succeed
    // enable it only after the first batch was processed
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v1/pods"))
            .withQueryParam("watch", equalTo("true"))
            .inScenario("broken batch")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withFixedDelay(100).withStatus(200).withBody("")));

    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v1/pods"))
            .withQueryParam("watch", equalTo("true"))
            .inScenario("broken batch")
            .whenScenarioStateIs("first batch was skipped")
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json;stream=watch")
                    .withBody(WatchEvent.added("add/pod.json").getContent())));

    runCachingAgentAndExpect(
        Map.of(
            POD_KIND,
            Set.of("kubernetes.v2:infrastructure:pod:my-account:test-namespace:my-pod-3")));
  }

  @Test
  @DisplayName("Should create ApiClient with server if server is provided")
  void createApiClientWithServer() {
    KubernetesNamedAccountCredentials mockedNamedAccountCredentials =
        Mockito.mock(KubernetesNamedAccountCredentials.class);
    KubernetesCredentials mockedCredentials = Mockito.mock(KubernetesCredentials.class);
    KubernetesStreamingCachingProperties mockedProperties =
        Mockito.mock(KubernetesStreamingCachingProperties.class);
    Registry mockedRegistry = Mockito.mock(Registry.class);
    ProviderCache mockedProviderCache = Mockito.mock(ProviderCache.class);

    // Registry/Id/Timer mock setup
    Id mockId = Mockito.mock(Id.class);
    Mockito.when(mockId.withTag(anyString(), anyString())).thenReturn(mockId);
    Mockito.when(mockedRegistry.createId(anyString())).thenReturn(mockId);

    Timer mockTimer = Mockito.mock(Timer.class);
    Counter mockCounter = Mockito.mock(Counter.class);
    Mockito.when(mockedRegistry.timer(anyString(), anyString(), anyString())).thenReturn(mockTimer);
    Mockito.when(
            mockedRegistry.timer(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(mockTimer);
    Mockito.when(mockedRegistry.counter(anyString(), anyString(), anyString()))
        .thenReturn(mockCounter);

    // Credentials setup
    Mockito.when(mockedNamedAccountCredentials.getStreamingCaching()).thenReturn(mockedProperties);
    Mockito.when(mockedNamedAccountCredentials.getCredentials()).thenReturn(mockedCredentials);
    Mockito.when(mockedCredentials.getAccountName()).thenReturn("acct");

    // Kubeconfig is null and server is set
    Mockito.when(mockedCredentials.getKubeconfigFile()).thenReturn(null);
    Mockito.when(mockedCredentials.getServer()).thenReturn("http://localhost:10111");

    KubernetesStreamingCachingAgentExecution mockedExecution =
        new KubernetesStreamingCachingAgentExecution(
            mockedNamedAccountCredentials,
            mockedProviderCache,
            List.of(),
            mockedRegistry,
            null,
            null);

    ApiClient apiClient = mockedExecution.createApiClient();
    assertThat(apiClient.getBasePath()).isEqualTo("http://localhost:10111");
  }

  private void runCachingAgentAndExpect(Map<String, Set<String>> expected)
      throws InterruptedException {
    Set<String> expectedTypes = expected.keySet();
    runCachingAgentAndAssert(
        (cache) -> assertThat(getCachedData(cache, expectedTypes)).isEqualTo(expected), 0);
  }

  private void runCachingAgentAndExpect(Map<String, Set<String>> expected, int paginationSize)
      throws InterruptedException {
    Set<String> expectedTypes = expected.keySet();
    runCachingAgentAndAssert(
        (cache) -> assertThat(getCachedData(cache, expectedTypes)).isEqualTo(expected),
        paginationSize);
  }

  private void runCachingAgentAndAssert(Consumer<ProviderCache> assertion, int paginationSize)
      throws InterruptedException {
    KubernetesConfigurationProperties configurationProperties =
        new KubernetesConfigurationProperties();
    KubernetesNamedAccountCredentials namedAccountCredentials = getNamedAccountCredentials();
    namedAccountCredentials.getStreamingCaching().setWatcherRetryTimeoutMillis(500);
    namedAccountCredentials.getStreamingCaching().setListTimeoutSeconds(2);
    namedAccountCredentials.getStreamingCaching().setWatchHeartbeatIntervalMillis(3_000);
    namedAccountCredentials.getStreamingCaching().setLivenessTimeoutMillis(5_000);
    namedAccountCredentials.getStreamingCaching().setWatchTimeoutSeconds(2);
    namedAccountCredentials.getStreamingCaching().setStopTimeoutMillis(3_000);
    namedAccountCredentials.getStreamingCaching().setBulkMaxWaitMillis(100);
    namedAccountCredentials.getStreamingCaching().setListPaginationSize(paginationSize);
    ExecutorService cleanupExecutorService = Executors.newFixedThreadPool(1);
    ExecutorService agentExecutorService = Executors.newSingleThreadExecutor();
    KubernetesStreamingCachingAgent cachingAgent =
        createCachingAgent(
            namedAccountCredentials, configurationProperties, cleanupExecutorService);

    ProviderCache cache = providerRegistry.getProviderCache(kubernetesProvider.getProviderName());
    LongRunningAgentExecution agentExecution = cachingAgent.getAgentExecution(providerRegistry);

    Future<?> executionFuture = null;
    Throwable failure = null;
    try {
      executionFuture =
          agentExecutorService.submit(() -> agentExecution.executeAgent(cachingAgent));

      awaitUntilAsserted(() -> assertion.accept(cache));
    } catch (Throwable t) {
      failure = t;
    } finally {
      try {
        agentExecution.stopExecutingAndCleanup().get(2_500, TimeUnit.MILLISECONDS);
      } catch (ExecutionException e) {
        failure = recordFailure(failure, e.getCause());
      } catch (TimeoutException e) {
        failure =
            recordFailure(failure, new AssertionError("Agent cleanup did not finish in time", e));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        failure = recordFailure(failure, e);
      }

      if (executionFuture != null) {
        try {
          executionFuture.get(500, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
          failure = recordFailure(failure, e.getCause());
        } catch (TimeoutException e) {
          failure =
              recordFailure(
                  failure, new AssertionError("Agent execution did not finish after cleanup", e));
          executionFuture.cancel(true);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          failure = recordFailure(failure, e);
          executionFuture.cancel(true);
        }
      }

      failure = shutDownExecutor(agentExecutorService, "agent", failure);
      failure = shutDownExecutor(cleanupExecutorService, "cleanup", failure);
    }

    propagateFailure(failure);
  }

  private static Throwable recordFailure(Throwable primary, Throwable additional) {
    if (primary == null) {
      return additional;
    }
    if (primary != additional) {
      primary.addSuppressed(additional);
    }
    return primary;
  }

  private static Throwable shutDownExecutor(
      ExecutorService executorService, String executorName, Throwable failure) {
    executorService.shutdown();
    try {
      if (!executorService.awaitTermination(500, TimeUnit.MILLISECONDS)) {
        failure =
            recordFailure(
                failure,
                new AssertionError(executorName + " executor did not terminate after cleanup"));
        executorService.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      failure = recordFailure(failure, e);
      executorService.shutdownNow();
    }
    return failure;
  }

  private static void propagateFailure(Throwable failure) throws InterruptedException {
    if (failure == null) {
      return;
    }
    if (failure instanceof InterruptedException interruptedException) {
      throw interruptedException;
    }
    if (failure instanceof RuntimeException runtimeException) {
      throw runtimeException;
    }
    if (failure instanceof Error error) {
      throw error;
    }
    throw new AssertionError(failure);
  }

  private void awaitUntilAsserted(Runnable assertion) {
    Duration pollInterval = Duration.ofMillis(100);
    Duration timeout = Duration.ofSeconds(5);

    long pollingEndedNanos = System.nanoTime() + timeout.toNanos();
    AssertionError assertionError = null;
    while (System.nanoTime() < pollingEndedNanos) {
      try {
        assertion.run();
        return; // success
      } catch (AssertionError e) {
        assertionError = e;
      }

      // Sleep for the poll interval before retrying
      try {
        Thread.sleep(pollInterval.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        assertionError.addSuppressed(e);
        throw new RuntimeException("Await interrupted", assertionError);
      }
    }

    // If we reach here, the timeout has expired
    throw new RuntimeException("Assertion failed after timeout", assertionError);
  }

  private Map<String, Set<String>> getCachedData(ProviderCache cache, Collection<String> types) {
    return types.stream()
        .collect(
            Collectors.toMap(Function.identity(), e -> new HashSet<>(cache.getIdentifiers(e))));
  }

  private static KubernetesStreamingCachingAgent createCachingAgent(
      KubernetesNamedAccountCredentials namedAccountCredentials,
      KubernetesConfigurationProperties configurationProperties,
      ExecutorService cleanupExecutorService) {
    return new KubernetesStreamingCachingAgent(
        namedAccountCredentials,
        configurationProperties,
        kubernetesSpinnakerKindMap,
        null,
        new NoopRegistry(),
        new NoOpStartupConcurrencyControl(),
        cleanupExecutorService);
  }
}
