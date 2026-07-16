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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;

import com.google.common.base.Suppliers;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.agent.LongRunningAgentExecution;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.AbstractKubernetesCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.Front50ApplicationLoader;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.kork.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

@Slf4j
public class KubernetesStreamingCachingAgent extends AbstractKubernetesCachingAgent
    implements CachingAgent {

  private static final String METRIC_PREFIX = "kubernetes.agent.streaming.buildCacheResult.";

  private final KubernetesNamedAccountCredentials namedAccountCredentials;
  private final Supplier<Set<String>> getDeclaredNamespaces;
  private final Registry registry;

  private final Timer elapsedTime;

  public KubernetesStreamingCachingAgent(
      KubernetesNamedAccountCredentials namedAccountCredentials,
      KubernetesConfigurationProperties configurationProperties,
      KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap,
      @Nullable Front50ApplicationLoader front50ApplicationLoader,
      Registry registry) {
    super(configurationProperties, kubernetesSpinnakerKindMap, front50ApplicationLoader);
    this.namedAccountCredentials = namedAccountCredentials;
    this.registry = registry;

    this.getDeclaredNamespaces =
        Suppliers.memoize(
            () -> {
              return new HashSet<>(
                  this.namedAccountCredentials.getCredentials().getDeclaredNamespaces());
            });

    String account = namedAccountCredentials.getCredentials().getAccountName();
    this.elapsedTime = registry.timer(METRIC_PREFIX + "elapsedTime", "account", account);
  }

  @Override
  public Set<AgentDataType> getProvidedDataTypes() {
    Stream<String> logicalTypes =
        Stream.of(Keys.LogicalKind.APPLICATIONS, Keys.LogicalKind.CLUSTERS).map(Enum::toString);
    Stream<String> kubernetesTypes = filteredPrimaryKinds().stream().map(KubernetesKind::toString);

    return Stream.concat(logicalTypes, kubernetesTypes)
        .map(AUTHORITATIVE::forType)
        .collect(toImmutableSet());
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    // the agent has to implement "CachingAgent" in order to be registered as an agent.
    // if it is not registered, a background thread will delete all its cached data.
    throw new UnsupportedOperationException(
        "KubernetesStreamingCachingAgent does not support loadData");
  }

  @Override
  public String getAgentType() {
    String accountName = namedAccountCredentials.getCredentials().getAccountName();
    return String.format(
        "%s/%s[%d/%d]",
        accountName, this.getClass().getSimpleName(), 1, 1); // do not support multiple agents
  }

  @Override
  public String getProviderName() {
    return KubernetesCloudProvider.ID;
  }

  @Override
  protected List<KubernetesKind> primaryKinds() {
    return namedAccountCredentials.getCredentials().getGlobalKinds();
  }

  @Override
  @VisibleForTesting
  protected List<KubernetesKind> filteredPrimaryKinds() {
    return super.filteredPrimaryKinds();
  }

  @Override
  public LongRunningAgentExecution getAgentExecution(ProviderRegistry providerRegistry) {
    ProviderCache cache = providerRegistry.getProviderCache(getProviderName());
    List<KubernetesKind> kubernetesKinds = filteredPrimaryKinds();
    return new KubernetesStreamingCachingAgentExecution(
        namedAccountCredentials, cache, kubernetesKinds, registry);
  }

  /**
   * Build cache result for the give "update" and "delete" events. The method returns a CacheResult
   * object that contains the cache data and evictions for declared namespaces only.
   */
  public CacheResult buildCacheResult(
      List<KubernetesManifest> updated, List<KubernetesManifest> deleted) {
    long startTime = System.nanoTime();
    String account = namedAccountCredentials.getCredentials().getAccountName();

    KubernetesCacheData kubernetesCacheData = new KubernetesCacheData();
    KubernetesCredentials credentials = namedAccountCredentials.getCredentials();
    Set<String> declaredNamespaces = getDeclaredNamespaces.get();

    AtomicInteger successfulCachedManifests = new AtomicInteger();
    AtomicInteger cachingFailures = new AtomicInteger();

    updated.stream()
        .peek(
            m -> {
              registry
                  .counter(
                      METRIC_PREFIX + "updatesProcessed",
                      "account",
                      account,
                      "kind",
                      m.getKindName())
                  .increment();
            })
        .filter(
            m ->
                declaredNamespaces.isEmpty()
                    || StringUtils.isEmpty(m.getNamespace()) // cluster-scoped resources
                    || declaredNamespaces.contains(m.getNamespace()))
        .peek(
            m ->
                credentials
                    .getResourcePropertyRegistry()
                    .get(m.getKind())
                    .getHandler()
                    .removeSensitiveKeys(m))
        .peek(m -> log.trace("building cache for {}", m))
        .filter(shouldCacheManifest(credentials))
        .forEach(
            rs -> {
              try {
                KubernetesCacheDataConverter.convertAsResource(
                    kubernetesCacheData,
                    namedAccountCredentials.getName(),
                    credentials.getKubernetesSpinnakerKindMap(),
                    credentials.getNamer(),
                    rs,
                    List.of(),
                    credentials.isCacheAllApplicationRelationships());
                successfulCachedManifests.incrementAndGet();
                registry
                    .counter(
                        METRIC_PREFIX + "successfullyProcessed",
                        "account",
                        account,
                        "kind",
                        rs.getKindName())
                    .increment();
              } catch (RuntimeException e) {
                log.warn(
                    "{}: Failure converting manifest: {}. Error: ",
                    getAgentType(),
                    rs.getFullResourceName(),
                    e);
                log.debug("{}: Failure converting {}. Error: ", getAgentType(), rs, e);
                cachingFailures.incrementAndGet();
                registry
                    .counter(
                        METRIC_PREFIX + "failedToProcess",
                        "account",
                        account,
                        "kind",
                        rs.getKindName())
                    .increment();
              }
            });

    Map<String, Collection<String>> evictions = computeEvictableData(deleted);
    Map<String, Collection<CacheData>> cachedData = kubernetesCacheData.toStratifiedCacheData();
    DefaultCacheResult result = new DefaultCacheResult(cachedData, evictions, Map.of(), true);

    int cachedEntriesTotal = cachedData.values().stream().mapToInt(Collection::size).sum();
    log.info(
        "{}: Results: Build cache result. New and updated manifests: {}, deleted: {}."
            + " Successful: {}, Failed: {}, Skipped: {}, Evictions: {}."
            + " Total Kubernetes caching groups: {}, containing: {} entries",
        getAgentType(),
        updated.size(),
        deleted.size(),
        successfulCachedManifests.get(),
        cachingFailures.get(),
        updated.size() - (successfulCachedManifests.get() + cachingFailures.get()),
        evictions.size(),
        cachedData.size(),
        cachedEntriesTotal);
    KubernetesCacheDataConverter.logStratifiedCacheData(getAgentType(), cachedData);

    long endTime = System.nanoTime();
    elapsedTime.record(endTime - startTime, TimeUnit.NANOSECONDS);

    return result;
  }

  private Map<String, Collection<String>> computeEvictableData(List<KubernetesManifest> deleted) {
    String account = namedAccountCredentials.getCredentials().getAccountName();
    Map<String, Collection<String>> evictionsByKey = new HashMap<>();
    for (KubernetesManifest manifest : deleted) {
      registry
          .counter(
              METRIC_PREFIX + "evictionsProcessed",
              "account",
              account,
              "kind",
              manifest.getKindName())
          .increment();
      // we evict resources from all namespaces even if the account is configured to only
      // cache resources in declared namespaces, because that's how the caching agent deletes
      // stale resources (e.g. if you delete a namespace from the account, we need to evict
      // all resources in that namespace)
      KubernetesKind kind = manifest.getKind();
      String namespace = manifest.getNamespace();
      String name = manifest.getName();
      Keys.CacheKey key =
          new Keys.InfrastructureCacheKey(kind, namedAccountCredentials.getName(), namespace, name);
      evictionsByKey.computeIfAbsent(key.getGroup(), k -> new ArrayList<>()).add(key.toString());
    }
    return evictionsByKey;
  }
}
