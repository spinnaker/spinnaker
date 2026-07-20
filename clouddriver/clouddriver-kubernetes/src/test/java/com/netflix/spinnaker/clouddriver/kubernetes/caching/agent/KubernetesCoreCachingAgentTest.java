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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultJsonCacheData;
import com.netflix.spinnaker.cats.mem.InMemoryCache;
import com.netflix.spinnaker.cats.provider.DefaultProviderCache;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.*;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.*;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;
import lombok.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import retrofit2.mock.Calls;

final class KubernetesCoreCachingAgentTest extends BaseKubernetesCachingAgentTest {
  /**
   * Given a KubernetesNamedAccountCredentials object and the number of caching agents to build,
   * builds a set of caching agents responsible for caching the account's data and returns a
   * collection of those agents.
   */
  private static ImmutableCollection<KubernetesCoreCachingAgent> createCachingAgents(
      KubernetesNamedAccountCredentials credentials,
      int agentCount,
      KubernetesConfigurationProperties configurationProperties) {
    return IntStream.range(0, agentCount)
        .mapToObj(
            i ->
                new KubernetesCoreCachingAgent(
                    credentials,
                    objectMapper,
                    new NoopRegistry(),
                    i,
                    agentCount,
                    10L,
                    configurationProperties,
                    kubernetesSpinnakerKindMap,
                    null))
        .collect(toImmutableList());
  }

  /**
   * Given a KubernetesNamedAccountCredentials object, the number of caching agents to build and
   * whether front50 needs to be queried for presence of an application, builds a set of caching
   * agents responsible for caching the account's data and returns a collection of those agents
   */
  private static ImmutableCollection<KubernetesCoreCachingAgent> createCachingAgents(
      KubernetesNamedAccountCredentials credentials,
      int agentCount,
      Front50ApplicationLoader front50ApplicationLoader,
      boolean checkApplicationInFront50) {
    KubernetesConfigurationProperties kubernetesConfigurationProperties =
        new KubernetesConfigurationProperties();
    if (!checkApplicationInFront50) {
      return createCachingAgents(credentials, agentCount, kubernetesConfigurationProperties);
    }

    kubernetesConfigurationProperties.getCache().setCheckApplicationInFront50(true);
    return IntStream.range(0, agentCount)
        .mapToObj(
            i ->
                new KubernetesCoreCachingAgent(
                    credentials,
                    objectMapper,
                    new NoopRegistry(),
                    i,
                    agentCount,
                    10L,
                    kubernetesConfigurationProperties,
                    kubernetesSpinnakerKindMap,
                    front50ApplicationLoader))
        .collect(toImmutableList());
  }

  /**
   * Given a collection of OnDemandAgent.OnDemandResult, return all cache results in these on-demand
   * results.
   */
  private static ImmutableMap<String, Collection<CacheData>> extractCacheResults(
      Collection<CacheResult> onDemandResults) {
    return onDemandResults.stream()
        .map(result -> result.getCacheResults().entrySet())
        .flatMap(Collection::stream)
        .collect(
            ImmutableSetMultimap.flatteningToImmutableSetMultimap(
                Map.Entry::getKey, e -> e.getValue().stream()))
        .asMap();
  }

  /** Given a collection of ProviderCache, return all on-demand entries in these caches. */
  private static ImmutableMap<String, Collection<DefaultJsonCacheData>> extractCacheEntries(
      Collection<ProviderCache> providerCaches) {
    return providerCaches.stream()
        .map(providerCache -> providerCache.getAll("onDemand"))
        .flatMap(Collection::stream)
        .filter(Objects::nonNull)
        .map(
            cacheData -> {
              try {
                return objectMapper.readValue(
                    (String) cacheData.getAttributes().get("cacheResults"),
                    new TypeReference<Map<String, Collection<DefaultJsonCacheData>>>() {});
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            })
        .map(Map::entrySet)
        .flatMap(Collection::stream)
        .collect(
            ImmutableSetMultimap.flatteningToImmutableSetMultimap(
                Map.Entry::getKey, e -> e.getValue().stream()))
        .asMap();
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 10})
  public void loadData(int numAgents) {
    String deploymentKey =
        Keys.InfrastructureCacheKey.createKey(
            KubernetesKind.DEPLOYMENT, ACCOUNT, NAMESPACE1, DEPLOYMENT_NAME);

    String storageClassKey =
        Keys.InfrastructureCacheKey.createKey(
            KubernetesKind.STORAGE_CLASS, ACCOUNT, "", STORAGE_CLASS_NAME);

    KubernetesConfigurationProperties configurationProperties =
        new KubernetesConfigurationProperties();
    configurationProperties.getCache().setCacheAll(true);

    ImmutableCollection<KubernetesCoreCachingAgent> cachingAgents =
        createCachingAgents(getNamedAccountCredentials(), numAgents, configurationProperties);
    LoadDataResult loadDataResult = processLoadData(cachingAgents, ImmutableMap.of());

    assertThat(loadDataResult.getResults()).containsKey(DEPLOYMENT_KIND);
    Collection<CacheData> deployments = loadDataResult.getResults().get(DEPLOYMENT_KIND);
    assertThat(deployments).extracting(CacheData::getId).containsExactly(deploymentKey);
    assertThat(deployments)
        .extracting(deployment -> deployment.getAttributes().get("name"))
        .containsExactly(DEPLOYMENT_NAME);

    // storage class kind should be cached
    validateStorageClassInCacheResult(storageClassKey, loadDataResult.getResults());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testCheckingOfApplicationsInFront50ForLoadData(boolean checkApplicationInFront50)
      throws JsonProcessingException {
    // setup:
    String deploymentKey =
        Keys.InfrastructureCacheKey.createKey(
            KubernetesKind.DEPLOYMENT, ACCOUNT, NAMESPACE1, DEPLOYMENT_NAME);

    Front50Service front50Service = mock(Front50Service.class);
    Front50ApplicationLoader front50ApplicationLoader =
        new Front50ApplicationLoader(front50Service);

    ImmutableCollection<KubernetesCoreCachingAgent> cachingAgents =
        createCachingAgents(
            getNamedAccountCredentials(), 1, front50ApplicationLoader, checkApplicationInFront50);

    when(front50Service.getAllApplicationsUnrestricted())
        .thenReturn(
            Calls.response(getApplicationsFromFront50("applications-response-from-front50.json")));
    front50ApplicationLoader.refreshCache();
    verify(front50Service).getAllApplicationsUnrestricted();

    // when:
    LoadDataResult loadDataResult = processLoadData(cachingAgents, ImmutableMap.of());

    // then:
    verifyNoMoreInteractions(front50Service);

    assertThat(loadDataResult.getResults()).containsKey(DEPLOYMENT_KIND);
    Collection<CacheData> deployments = loadDataResult.getResults().get(DEPLOYMENT_KIND);
    assertThat(deployments).extracting(CacheData::getId).containsExactly(deploymentKey);
    assertThat(deployments)
        .extracting(deployment -> deployment.getAttributes().get("name"))
        .containsExactly(DEPLOYMENT_NAME);
  }

  @Test
  public void testK8sManifestWithNoApplicationInFront50ShouldNotBeCachedInLoadData()
      throws JsonProcessingException {
    // setup:
    String deploymentName = "some-name-not-in-front50";

    Front50Service front50Service = mock(Front50Service.class);
    Front50ApplicationLoader front50ApplicationLoader =
        new Front50ApplicationLoader(front50Service);

    ImmutableCollection<KubernetesCoreCachingAgent> cachingAgents =
        createCachingAgents(
            getNamedAccountCredentials(deploymentName), 1, front50ApplicationLoader, true);

    when(front50Service.getAllApplicationsUnrestricted())
        .thenReturn(
            Calls.response(
                getApplicationsFromFront50("applications-response-from-front50-2.json")));

    front50ApplicationLoader.refreshCache();
    verify(front50Service).getAllApplicationsUnrestricted();

    // when:
    LoadDataResult loadDataResult = processLoadData(cachingAgents, ImmutableMap.of());

    // then:
    verifyNoMoreInteractions(front50Service);

    // the deployment should not be cached as its application is not known to front50
    assertThat(loadDataResult.getResults()).doesNotContainKey(DEPLOYMENT_KIND);
    Collection<CacheData> deployments = loadDataResult.getResults().get(DEPLOYMENT_KIND);
    assertThat(deployments).isNullOrEmpty();
  }

  /**
   * Given an on-demand cache request, constructs a set of caching agents and sends the on-demand
   * request to those agents, returning a collection of all non-null results of handing those
   * requests. Any cache entries in primeCacheData will be added to each agent's backing cache
   * before processing the request.
   */
  private static LoadDataResult processLoadData(
      Collection<KubernetesCoreCachingAgent> cachingAgents,
      Map<String, Collection<CacheData>> primeCacheData) {
    ImmutableList.Builder<CacheResult> resultBuilder = new ImmutableList.Builder<>();
    ImmutableList.Builder<ProviderCache> providerCacheBuilder = new ImmutableList.Builder<>();
    cachingAgents.forEach(
        cachingAgent -> {
          ProviderCache providerCache = new DefaultProviderCache(new InMemoryCache());
          providerCacheBuilder.add(providerCache);
          for (String type : primeCacheData.keySet()) {
            for (CacheData cacheData : primeCacheData.get(type)) {
              providerCache.putCacheData(type, cacheData);
            }
          }
          CacheResult result = cachingAgent.loadData(providerCache);
          if (result != null) {
            resultBuilder.add(result);
          }
        });
    return new LoadDataResult(resultBuilder.build(), providerCacheBuilder.build());
  }

  @Value
  private static class LoadDataResult {
    Map<String, Collection<CacheData>> results;
    Map<String, Collection<DefaultJsonCacheData>> cacheEntries;

    LoadDataResult(
        Collection<CacheResult> loadDataResults, Collection<ProviderCache> providerCaches) {
      this.results = extractCacheResults(loadDataResults);
      this.cacheEntries = extractCacheEntries(providerCaches);
    }
  }

  /**
   * See comment in {@link KubernetesCoreCachingAgent#getProvidedDataTypes()} for why we are
   * continuing to use the deprecated Keys.Kind.ARTIFACT.
   */
  @SuppressWarnings("deprecation")
  @ParameterizedTest
  @ValueSource(ints = {1, 2, 10})
  public void authoritativeForLogicalTypes(int numAgents) {
    KubernetesConfigurationProperties configurationProperties =
        new KubernetesConfigurationProperties();
    configurationProperties.getCache().setCacheAll(true);

    ImmutableCollection<KubernetesCoreCachingAgent> cachingAgents =
        createCachingAgents(getNamedAccountCredentials(), numAgents, configurationProperties);
    cachingAgents.forEach(
        cachingAgent ->
            assertThat(getAuthoritativeTypes(cachingAgent.getProvidedDataTypes()))
                .containsAll(
                    ImmutableList.of(
                        Keys.LogicalKind.APPLICATIONS.toString(),
                        Keys.LogicalKind.CLUSTERS.toString(),
                        Keys.Kind.ARTIFACT.toString())));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 10})
  public void authoritativeForKubernetesKinds(int numAgents) {
    KubernetesConfigurationProperties configurationProperties =
        new KubernetesConfigurationProperties();
    configurationProperties.getCache().setCacheAll(true);

    ImmutableCollection<KubernetesCoreCachingAgent> cachingAgents =
        createCachingAgents(getNamedAccountCredentials(), numAgents, configurationProperties);
    cachingAgents.forEach(
        cachingAgent ->
            assertThat(getAuthoritativeTypes(cachingAgent.getProvidedDataTypes()))
                .containsAll(
                    ImmutableList.of(
                        KubernetesKind.NAMESPACE.toString(),
                        KubernetesKind.POD.toString(),
                        KubernetesKind.REPLICA_SET.toString())));
  }

  /**
   * filteredPrimaryKinds returns all registered core kinds, coming from {@link
   * KubernetesCoreCachingAgentTest#kindProperties}
   */
  @Test
  public void filteredPrimaryKindsAll() {
    KubernetesConfigurationProperties configurationProperties =
        new KubernetesConfigurationProperties();
    configurationProperties.getCache().setCacheAll(true);
    KubernetesNamedAccountCredentials namedAccountCredentials = getNamedAccountCredentials();
    KubernetesCoreCachingAgent cachingAgent =
        createCachingAgents(namedAccountCredentials, 1, configurationProperties).asList().get(0);

    List<KubernetesKind> filteredPrimaryKinds = cachingAgent.filteredPrimaryKinds();

    KubernetesKind[] expected = kindProperties.keySet().toArray(new KubernetesKind[0]);
    assertThat(filteredPrimaryKinds)
        .containsExactlyInAnyOrder(expected); // has everything in global kinds
  }

  /**
   * filteredPrimaryKinds returns only core kinds specified in {@link
   * KubernetesConfigurationProperties.Cache#getCacheKinds()}
   */
  @Test
  public void filteredPrimaryKindsFromConfig() {
    KubernetesConfigurationProperties configurationProperties =
        new KubernetesConfigurationProperties();
    configurationProperties.getCache().setCacheAll(false);
    configurationProperties
        .getCache()
        .setCacheKinds(Arrays.asList("deployment", "myCustomKind.my.group"));
    KubernetesCoreCachingAgent cachingAgent =
        createCachingAgents(getNamedAccountCredentials(), 1, configurationProperties)
            .asList()
            .get(0);

    List<KubernetesKind> filteredPrimaryKinds = cachingAgent.filteredPrimaryKinds();

    assertThat(filteredPrimaryKinds)
        .containsExactlyInAnyOrder(KubernetesKind.fromString("deployment")); // only has core kinds
  }

  /**
   * filteredPrimaryKinds returns only core kinds mapped to SpinnakerKinds that show in classic
   * infrastructure screens {@link KubernetesCachingAgent#SPINNAKER_UI_KINDS}
   */
  @Test
  public void filteredPrimaryKindsSpinnakerUI() {
    KubernetesConfigurationProperties configurationProperties =
        new KubernetesConfigurationProperties();
    KubernetesCoreCachingAgent cachingAgent =
        createCachingAgents(getNamedAccountCredentials(), 1, configurationProperties)
            .asList()
            .get(0);

    List<KubernetesKind> filteredPrimaryKinds = cachingAgent.filteredPrimaryKinds();

    KubernetesKind[] expected =
        KubernetesCachingAgent.SPINNAKER_UI_KINDS.stream()
            .map(kubernetesSpinnakerKindMap::translateSpinnakerKind)
            .flatMap(Collection::stream)
            .filter(kindProperties::containsKey)
            .toArray(KubernetesKind[]::new);
    assertThat(filteredPrimaryKinds).containsExactlyInAnyOrder(expected); // only has UI kinds
  }

  /**
   * filteredPrimaryKinds doesn't include kinds specified in {@link
   * KubernetesConfigurationProperties.Cache#getCacheOmitKinds()}
   */
  @Test
  public void filteredPrimaryKindsOmitKind() {
    KubernetesConfigurationProperties configurationProperties =
        new KubernetesConfigurationProperties();
    configurationProperties.getCache().setCacheOmitKinds(Collections.singletonList("deployment"));
    KubernetesCoreCachingAgent cachingAgent =
        createCachingAgents(getNamedAccountCredentials(), 1, configurationProperties)
            .asList()
            .get(0);

    List<KubernetesKind> filteredPrimaryKinds = cachingAgent.filteredPrimaryKinds();

    KubernetesKind[] expected =
        KubernetesCachingAgent.SPINNAKER_UI_KINDS.stream()
            .map(kubernetesSpinnakerKindMap::translateSpinnakerKind)
            .flatMap(Collection::stream)
            .filter(kindProperties::containsKey)
            .filter(k -> !k.equals(KubernetesKind.DEPLOYMENT))
            .toArray(KubernetesKind[]::new);
    assertThat(filteredPrimaryKinds).containsExactlyInAnyOrder(expected); // excludes Deployment
  }
}
