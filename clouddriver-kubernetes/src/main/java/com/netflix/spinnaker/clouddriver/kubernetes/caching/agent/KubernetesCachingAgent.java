/*
 * Copyright 2017 Google, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.AgentIntervalAware;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesCachingPolicy;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesCachingProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKindProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKindProperties.ResourceScope;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestAnnotater;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

/**
 * A kubernetes caching agent is a class that caches part of the kubernetes infrastructure. Every
 * instance of a caching agent is responsible for caching only one account, and only some (but not
 * all) kubernetes kinds of that account.
 */
public abstract class KubernetesCachingAgent
    implements AgentIntervalAware, CachingAgent, AccountAware {
  private static final Logger log = LoggerFactory.getLogger(KubernetesCachingAgent.class);

  public static final List<SpinnakerKind> SPINNAKER_UI_KINDS =
      Arrays.asList(
          SpinnakerKind.SERVER_GROUP_MANAGERS,
          SpinnakerKind.SERVER_GROUPS,
          SpinnakerKind.INSTANCES,
          SpinnakerKind.LOAD_BALANCERS,
          SpinnakerKind.SECURITY_GROUPS);

  @Getter @Nonnull protected final String accountName;
  protected final Registry registry;
  protected final KubernetesCredentials credentials;
  protected final ObjectMapper objectMapper;

  protected final int agentIndex;
  protected final int agentCount;

  @Getter protected String providerName = KubernetesCloudProvider.ID;

  @Getter protected final Long agentInterval;

  protected final KubernetesConfigurationProperties configurationProperties;

  protected final KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap;
  @Nullable private final Front50ApplicationLoader front50ApplicationLoader;

  protected KubernetesCachingAgent(
      KubernetesNamedAccountCredentials namedAccountCredentials,
      ObjectMapper objectMapper,
      Registry registry,
      int agentIndex,
      int agentCount,
      Long agentInterval,
      KubernetesConfigurationProperties configurationProperties,
      KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap,
      @Nullable Front50ApplicationLoader front50ApplicationLoader) {
    this.accountName = namedAccountCredentials.getName();
    this.credentials = namedAccountCredentials.getCredentials();
    this.objectMapper = objectMapper;
    this.registry = registry;
    this.agentIndex = agentIndex;
    this.agentCount = agentCount;
    this.agentInterval = agentInterval;
    this.configurationProperties = configurationProperties;
    this.kubernetesSpinnakerKindMap = kubernetesSpinnakerKindMap;
    this.front50ApplicationLoader = front50ApplicationLoader;
  }

  protected Map<String, Object> defaultIntrospectionDetails() {
    Map<String, Object> result = new HashMap<>();
    result.put("namespaces", getNamespaces());
    result.put("kinds", filteredPrimaryKinds());
    return result;
  }

  protected abstract List<KubernetesKind> primaryKinds();

  /**
   * Filters the list of kinds returned from primaryKinds according to configuration.
   *
   * @return filtered list of primaryKinds.
   */
  protected List<KubernetesKind> filteredPrimaryKinds() {
    List<KubernetesKind> primaryKinds = primaryKinds();
    List<KubernetesKind> filteredPrimaryKinds;

    if (configurationProperties.getCache().isCacheAll()) {
      filteredPrimaryKinds = primaryKinds;

    } else if (configurationProperties.getCache().getCacheKinds() != null
        && configurationProperties.getCache().getCacheKinds().size() > 0) {
      // If provider config specifies what kinds to cache, use it
      filteredPrimaryKinds =
          configurationProperties.getCache().getCacheKinds().stream()
              .map(KubernetesKind::fromString)
              .filter(primaryKinds::contains)
              .collect(Collectors.toList());

    } else {
      // Only cache kinds used in Spinnaker's classic infrastructure screens, which are the kinds
      // mapped to Spinnaker kinds like ServerGroups, Instances, etc.
      filteredPrimaryKinds =
          SPINNAKER_UI_KINDS.stream()
              .map(kubernetesSpinnakerKindMap::translateSpinnakerKind)
              .flatMap(Collection::stream)
              .filter(primaryKinds::contains)
              .collect(Collectors.toList());
    }

    // Filter out explicitly omitted kinds in provider config
    if (configurationProperties.getCache().getCacheOmitKinds() != null
        && configurationProperties.getCache().getCacheOmitKinds().size() > 0) {
      List<KubernetesKind> omitKinds =
          configurationProperties.getCache().getCacheOmitKinds().stream()
              .map(KubernetesKind::fromString)
              .collect(Collectors.toList());
      filteredPrimaryKinds =
          filteredPrimaryKinds.stream()
              .filter(k -> !omitKinds.contains(k))
              .collect(Collectors.toList());
    }

    return filteredPrimaryKinds;
  }

  private ImmutableList<KubernetesManifest> loadResources(
      @Nonnull Iterable<KubernetesKind> kubernetesKinds, Optional<String> optionalNamespace) {
    String namespace = optionalNamespace.orElse(null);
    return credentials.list(ImmutableList.copyOf(kubernetesKinds), namespace);
  }

  @Nonnull
  private ImmutableList<KubernetesManifest> loadNamespaceScopedResources(
      @Nonnull Iterable<KubernetesKind> kubernetesKinds) {
    return getNamespaces()
        // Not using parallelStream. In ForkJoin.commonPool, the number of threads == (CPU cores -
        // 1). Since we're already running in the AgentExecutionAction thread pool and the number of
        // threads to compute namespaces is already configurable at account level, this is not
        // needed and most importantly, avoids contention in the common pool, increasing
        // performance.
        .stream()
        .map(n -> loadResources(kubernetesKinds, Optional.of(n)))
        .flatMap(Collection::stream)
        .collect(ImmutableList.toImmutableList());
  }

  @Nonnull
  private ImmutableList<KubernetesManifest> loadClusterScopedResources(
      @Nonnull Iterable<KubernetesKind> kubernetesKinds) {
    if (handleClusterScopedResources()) {
      return loadResources(kubernetesKinds, Optional.empty());
    } else {
      return ImmutableList.of();
    }
  }

  private ImmutableSetMultimap<ResourceScope, KubernetesKind> primaryKindsByScope() {
    return filteredPrimaryKinds().stream()
        .collect(
            ImmutableSetMultimap.toImmutableSetMultimap(
                k -> credentials.getKindProperties(k).getResourceScope(), Function.identity()));
  }

  protected Map<KubernetesKind, List<KubernetesManifest>> loadPrimaryResourceList() {
    ImmutableSetMultimap<ResourceScope, KubernetesKind> kindsByScope = primaryKindsByScope();

    Map<KubernetesKind, List<KubernetesManifest>> result =
        Stream.concat(
                loadClusterScopedResources(
                    kindsByScope.get(KubernetesKindProperties.ResourceScope.CLUSTER))
                    .stream(),
                loadNamespaceScopedResources(
                    kindsByScope.get(KubernetesKindProperties.ResourceScope.NAMESPACE))
                    .stream())
            .collect(Collectors.groupingBy(KubernetesManifest::getKind));

    for (KubernetesCachingPolicy policy : credentials.getCachingPolicies()) {
      KubernetesKind policyKind = KubernetesKind.fromString(policy.getKubernetesKind());
      if (!result.containsKey(policyKind)) {
        continue;
      }

      List<KubernetesManifest> entries = result.get(policyKind);
      if (entries == null) {
        continue;
      }

      if (entries.size() > policy.getMaxEntriesPerAgent()) {
        log.warn(
            "{}: Pruning {} entries from kind {}",
            getAgentType(),
            entries.size() - policy.getMaxEntriesPerAgent(),
            policyKind);
        entries = entries.subList(0, policy.getMaxEntriesPerAgent());
        result.put(policyKind, entries);
      }
    }

    return result;
  }

  /**
   * Deprecated in favor {@link KubernetesCachingAgent#loadPrimaryResource(KubernetesCoordinates)}.
   */
  @Deprecated
  protected KubernetesManifest loadPrimaryResource(
      KubernetesKind kind, String namespace, String name) {
    return loadPrimaryResource(
        KubernetesCoordinates.builder().kind(kind).namespace(namespace).name(name).build());
  }

  protected KubernetesManifest loadPrimaryResource(KubernetesCoordinates coordinates) {
    return credentials.get(coordinates);
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info(getAgentType() + ": agent is starting");
    Map<String, Object> details = defaultIntrospectionDetails();

    long start = System.currentTimeMillis();
    Map<KubernetesKind, List<KubernetesManifest>> primaryResourceList = loadPrimaryResourceList();
    details.put("timeSpentInKubectlMs", System.currentTimeMillis() - start);
    return buildCacheResult(primaryResourceList);
  }

  protected CacheResult buildCacheResult(KubernetesManifest resource) {
    return buildCacheResult(ImmutableMap.of(resource.getKind(), ImmutableList.of(resource)));
  }

  /**
   * method that determines if the provided manifest should be cached or not. It makes that
   * determination based on the following rules:
   *
   * <p>- if a manifest's caching properties has ignore == true, then it will not be cached.
   *
   * <p>- Otherwise, if account is configured to be "onlySpinnakerManaged", and
   * "moniker.spinnaker.io/application" annotation is empty, then it will not be cached.
   *
   * <p>- if {@link KubernetesConfigurationProperties.Cache#isCheckApplicationInFront50()} is true,
   * and the application name obtained from the manifest is not known to front50, then the manifest
   * will not be cached as long as it belongs to one of the logical relationship kinds specified in
   * {@link KubernetesCacheDataConverter#getLogicalRelationshipKinds()}.
   *
   * <p>- If none of the above criteria is satisfied, then the manifest will be cached.
   *
   * @param credentials account credentials
   * @return true, if manifest should be cached, false otherwise
   */
  private Predicate<KubernetesManifest> shouldCacheManifest(KubernetesCredentials credentials) {
    return m -> {
      KubernetesCachingProperties props = KubernetesManifestAnnotater.getCachingProperties(m);
      if (props.isIgnore()) {
        return false;
      }

      if (credentials.isOnlySpinnakerManaged() && props.getApplication().isEmpty()) {
        return false;
      }

      if (configurationProperties.getCache().isCheckApplicationInFront50()) {
        // only certain type of kinds are stored in cats_v1_applications table
        SpinnakerKind spinnakerKind =
            credentials.getKubernetesSpinnakerKindMap().translateKubernetesKind(m.getKind());
        log.debug(
            "{}: manifest: {}, kind: {}, spinnakerKind: {}, logicalRelationshipKinds: {}",
            getAgentType(),
            m.getFullResourceName(),
            m.getKind(),
            spinnakerKind,
            KubernetesCacheDataConverter.getLogicalRelationshipKinds());
        if (KubernetesCacheDataConverter.getLogicalRelationshipKinds().contains(spinnakerKind)) {
          if (front50ApplicationLoader == null) {
            return false;
          }

          String appNameFromMoniker = credentials.getNamer().deriveMoniker(m).getApp();

          boolean shouldCache =
              front50ApplicationLoader.getData().stream()
                  .anyMatch(app -> app.equalsIgnoreCase(appNameFromMoniker));

          log.debug(
              "{}: manifest: {}, application name: {}, shouldCache: {}",
              getAgentType(),
              m.getFullResourceName(),
              appNameFromMoniker,
              shouldCache);

          return shouldCache;
        }
      }
      return true;
    };
  }

  protected CacheResult buildCacheResult(Map<KubernetesKind, List<KubernetesManifest>> resources) {
    if (resources.isEmpty()) {
      log.info("{} did not find anything to cache", getAgentType());
      return new DefaultCacheResult(Map.of());
    }

    KubernetesCacheData kubernetesCacheData = new KubernetesCacheData();
    Map<KubernetesManifest, List<KubernetesManifest>> relationships =
        loadSecondaryResourceRelationships(resources);

    AtomicInteger successfulCachedManifests = new AtomicInteger();
    AtomicInteger cachingFailures = new AtomicInteger();
    resources.values().stream()
        .flatMap(Collection::stream)
        .peek(
            m ->
                credentials
                    .getResourcePropertyRegistry()
                    .get(m.getKind())
                    .getHandler()
                    .removeSensitiveKeys(m))
        .filter(shouldCacheManifest(credentials))
        .forEach(
            rs -> {
              try {
                KubernetesCacheDataConverter.convertAsResource(
                    kubernetesCacheData,
                    accountName,
                    credentials.getKubernetesSpinnakerKindMap(),
                    credentials.getNamer(),
                    rs,
                    relationships.getOrDefault(rs, ImmutableList.of()),
                    credentials.isCacheAllApplicationRelationships());
                successfulCachedManifests.incrementAndGet();
              } catch (RuntimeException e) {
                log.warn(
                    "{}: Failure converting manifest: {}. Error: ",
                    getAgentType(),
                    rs.getFullResourceName(),
                    e);
                log.debug("{}: Failure converting {}. Error: ", getAgentType(), rs, e);
                cachingFailures.incrementAndGet();
              }
            });

    Map<String, Collection<CacheData>> entries = kubernetesCacheData.toStratifiedCacheData();
    int total = resources.values().stream().mapToInt(List::size).sum();
    int cachedEntriesTotal = entries.values().stream().mapToInt(Collection::size).sum();
    log.info(
        "{}: Results: Attempted to cache {} manifests, belonging to {} kinds."
            + " Successful: {}, Failed: {}, Skipped: {},"
            + " Total Kubernetes caching groups: {}, containing: {} entries",
        getAgentType(),
        total,
        resources.size(),
        successfulCachedManifests.get(),
        cachingFailures.get(),
        total - (successfulCachedManifests.get() + cachingFailures.get()),
        entries.size(),
        cachedEntriesTotal);
    KubernetesCacheDataConverter.logStratifiedCacheData(getAgentType(), entries);

    return new DefaultCacheResult(entries);
  }

  protected Map<KubernetesManifest, List<KubernetesManifest>> loadSecondaryResourceRelationships(
      Map<KubernetesKind, List<KubernetesManifest>> allResources) {
    Map<KubernetesManifest, List<KubernetesManifest>> result = new HashMap<>();
    allResources
        .keySet()
        .forEach(
            k -> {
              try {
                credentials
                    .getResourcePropertyRegistry()
                    .get(k)
                    .getHandler()
                    .addRelationships(allResources, result);
              } catch (RuntimeException e) {
                log.warn("{}: Failure adding relationships for {}", getAgentType(), k, e);
              }
            });
    return result;
  }

  protected ImmutableList<String> getNamespaces() {
    return credentials.getDeclaredNamespaces().stream()
        .filter(n -> agentCount == 1 || Math.abs(n.hashCode() % agentCount) == agentIndex)
        .collect(ImmutableList.toImmutableList());
  }

  /**
   * Should this caching agent be responsible for caching cluster-scoped resources (ie, those that
   * do not live in a particular namespace)?
   */
  protected boolean handleClusterScopedResources() {
    return agentIndex == 0;
  }

  @Override
  public String getAgentType() {
    return String.format(
        "%s/%s[%d/%d]", accountName, this.getClass().getSimpleName(), agentIndex + 1, agentCount);
  }
}
