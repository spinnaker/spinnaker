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
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultJsonCacheData;
import com.netflix.spinnaker.cats.mem.InMemoryCache;
import com.netflix.spinnaker.cats.provider.DefaultProviderCache;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.GlobalResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.description.ResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.*;
import com.netflix.spinnaker.clouddriver.kubernetes.names.KubernetesManifestNamer;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesUnregisteredCustomResourceHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.moniker.Namer;
import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;
import lombok.Value;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;

@RunWith(JUnitPlatform.class)
final class KubernetesCoreCachingAgentTest {
  private static final String ACCOUNT = "my-account";
  private static final String NAMESPACE1 = "test-namespace";
  private static final String NAMESPACE2 = "test-namespace2";
  private static final String DEPLOYMENT_NAME = "my-deployment";
  private static final String STORAGE_CLASS_NAME = "my-storage-class";

  private static final String DEPLOYMENT_KIND = KubernetesKind.DEPLOYMENT.toString();
  private static final String STORAGE_CLASS_KIND = KubernetesKind.STORAGE_CLASS.toString();

  private static final ImmutableMap<KubernetesKind, KubernetesKindProperties> kindProperties =
      ImmutableMap.<KubernetesKind, KubernetesKindProperties>builder()
          .put(
              KubernetesKind.DEPLOYMENT,
              KubernetesKindProperties.create(KubernetesKind.DEPLOYMENT, true))
          .put(
              KubernetesKind.STORAGE_CLASS,
              KubernetesKindProperties.create(KubernetesKind.STORAGE_CLASS, false))
          .put(
              KubernetesKind.NAMESPACE,
              KubernetesKindProperties.create(KubernetesKind.NAMESPACE, false))
          .put(KubernetesKind.POD, KubernetesKindProperties.create(KubernetesKind.POD, true))
          .put(
              KubernetesKind.REPLICA_SET,
              KubernetesKindProperties.create(KubernetesKind.REPLICA_SET, true))
          .build();

  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final ResourcePropertyRegistry resourcePropertyRegistry =
      new GlobalResourcePropertyRegistry(
          ImmutableList.of(), new KubernetesUnregisteredCustomResourceHandler());
  private static final Namer<KubernetesManifest> NAMER = new KubernetesManifestNamer();

  /** A test Deployment manifest */
  private static KubernetesManifest deploymentManifest() {
    KubernetesManifest deployment = new KubernetesManifest();
    deployment.put("metadata", new HashMap<>());
    deployment.setNamespace(NAMESPACE1);
    deployment.setKind(KubernetesKind.DEPLOYMENT);
    deployment.setApiVersion(KubernetesApiVersion.APPS_V1);
    deployment.setName(DEPLOYMENT_NAME);
    return deployment;
  }

  /** A test StorageClass manifest object */
  private static KubernetesManifest storageClassManifest() {
    KubernetesManifest storageClass = new KubernetesManifest();
    storageClass.put("metadata", new HashMap<>());
    storageClass.setKind(KubernetesKind.STORAGE_CLASS);
    storageClass.setApiVersion(KubernetesApiVersion.fromString("storage.k8s.io/v1"));
    storageClass.setName(STORAGE_CLASS_NAME);
    return storageClass;
  }

  /** Returns a mock KubernetesCredentials object */
  private static KubernetesCredentials mockKubernetesCredentials() {
    KubernetesCredentials credentials = mock(KubernetesCredentials.class);
    when(credentials.getGlobalKinds()).thenReturn(kindProperties.keySet().asList());
    when(credentials.getKindProperties(any(KubernetesKind.class)))
        .thenAnswer(invocation -> kindProperties.get(invocation.getArgument(0)));
    when(credentials.getDeclaredNamespaces()).thenReturn(ImmutableList.of(NAMESPACE1, NAMESPACE2));
    when(credentials.getResourcePropertyRegistry()).thenReturn(resourcePropertyRegistry);
    when(credentials.get(
            KubernetesCoordinates.builder()
                .kind(KubernetesKind.DEPLOYMENT)
                .namespace(NAMESPACE1)
                .name(DEPLOYMENT_NAME)
                .build()))
        .thenReturn(deploymentManifest());
    when(credentials.get(
            KubernetesCoordinates.builder()
                .kind(KubernetesKind.STORAGE_CLASS)
                .name(STORAGE_CLASS_NAME)
                .build()))
        .thenReturn(storageClassManifest());
    when(credentials.list(any(List.class), any()))
        .thenAnswer(
            (Answer<ImmutableList<KubernetesManifest>>)
                invocation -> {
                  Object[] args = invocation.getArguments();
                  ImmutableSet<KubernetesKind> kinds =
                      ImmutableSet.copyOf((List<KubernetesKind>) args[0]);
                  String namespace = (String) args[1];
                  ImmutableList.Builder<KubernetesManifest> result = new ImmutableList.Builder<>();
                  if (kinds.contains(KubernetesKind.DEPLOYMENT) && NAMESPACE1.equals(namespace)) {
                    result.add(deploymentManifest());
                  }
                  if (kinds.contains(KubernetesKind.STORAGE_CLASS)) {
                    result.add(storageClassManifest());
                  }
                  return result.build();
                });
    when(credentials.getNamer()).thenReturn(NAMER);
    return credentials;
  }

  /**
   * Returns a KubernetesNamedAccountCredentials that contains a mock KubernetesCredentials object
   */
  private static KubernetesNamedAccountCredentials getNamedAccountCredentials() {
    KubernetesConfigurationProperties.ManagedAccount managedAccount =
        new KubernetesConfigurationProperties.ManagedAccount();
    managedAccount.setName(ACCOUNT);

    KubernetesCredentials mockCredentials = mockKubernetesCredentials();
    KubernetesCredentials.Factory credentialFactory = mock(KubernetesCredentials.Factory.class);
    when(credentialFactory.build(managedAccount)).thenReturn(mockCredentials);
    return new KubernetesNamedAccountCredentials(managedAccount, credentialFactory);
  }

  /**
   * Given a KubernetesNamedAccountCredentials object and the number of caching agents to build,
   * builds a set of caching agents responsible for caching the account's data and returns a
   * collection of those agents.
   */
  private static ImmutableCollection<KubernetesCoreCachingAgent> createCachingAgents(
      KubernetesNamedAccountCredentials credentials, int agentCount) {
    return IntStream.range(0, agentCount)
        .mapToObj(
            i ->
                new KubernetesCoreCachingAgent(
                    credentials, objectMapper, new NoopRegistry(), i, agentCount, 10L))
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

    ImmutableCollection<KubernetesCoreCachingAgent> cachingAgents =
        createCachingAgents(getNamedAccountCredentials(), numAgents);
    LoadDataResult loadDataResult = processLoadData(cachingAgents, ImmutableMap.of());

    assertThat(loadDataResult.getResults()).containsKey(DEPLOYMENT_KIND);
    Collection<CacheData> deployments = loadDataResult.getResults().get(DEPLOYMENT_KIND);
    assertThat(deployments).extracting(CacheData::getId).containsExactly(deploymentKey);
    assertThat(deployments)
        .extracting(deployment -> deployment.getAttributes().get("name"))
        .containsExactly(DEPLOYMENT_NAME);

    assertThat(loadDataResult.getResults()).containsKey(STORAGE_CLASS_KIND);
    Collection<CacheData> storageClasses = loadDataResult.getResults().get(STORAGE_CLASS_KIND);
    assertThat(storageClasses).extracting(CacheData::getId).contains(storageClassKey);
    assertThat(storageClasses)
        .extracting(storageClass -> storageClass.getAttributes().get("name"))
        .containsExactly(STORAGE_CLASS_NAME);
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
    ImmutableCollection<KubernetesCoreCachingAgent> cachingAgents =
        createCachingAgents(getNamedAccountCredentials(), numAgents);
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
    ImmutableCollection<KubernetesCoreCachingAgent> cachingAgents =
        createCachingAgents(getNamedAccountCredentials(), numAgents);
    cachingAgents.forEach(
        cachingAgent ->
            assertThat(getAuthoritativeTypes(cachingAgent.getProvidedDataTypes()))
                .containsAll(
                    ImmutableList.of(
                        KubernetesKind.NAMESPACE.toString(),
                        KubernetesKind.POD.toString(),
                        KubernetesKind.REPLICA_SET.toString())));
  }

  private static ImmutableList<String> getAuthoritativeTypes(
      Collection<AgentDataType> agentDataTypes) {
    return agentDataTypes.stream()
        .filter(dataType -> dataType.getAuthority() == AUTHORITATIVE)
        .map(AgentDataType::getTypeName)
        .collect(toImmutableList());
  }
}
