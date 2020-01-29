/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentIntervalAware;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.KubernetesCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesCachingPolicy;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.RegistryUtils;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKindProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKindProperties.ResourceScope;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor.KubectlException;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class KubernetesV2CachingAgent
    extends KubernetesCachingAgent<KubernetesV2Credentials> implements AgentIntervalAware {
  protected KubectlJobExecutor jobExecutor;

  @Getter protected String providerName = KubernetesCloudProvider.ID;

  @Getter protected final Long agentInterval;

  protected KubernetesV2CachingAgent(
      KubernetesNamedAccountCredentials<KubernetesV2Credentials> namedAccountCredentials,
      ObjectMapper objectMapper,
      Registry registry,
      int agentIndex,
      int agentCount,
      Long agentInterval) {
    super(namedAccountCredentials, objectMapper, registry, agentIndex, agentCount);
    this.agentInterval = agentInterval;
  }

  protected Map<String, Object> defaultIntrospectionDetails() {
    Map<String, Object> result = new HashMap<>();
    result.put("namespaces", getNamespaces());
    result.put("kinds", primaryKinds());
    return result;
  }

  protected abstract List<KubernetesKind> primaryKinds();

  private ImmutableList<KubernetesManifest> loadResources(
      @Nonnull Iterable<KubernetesKind> kubernetesKinds, Optional<String> optionalNamespace) {
    String namespace = optionalNamespace.orElse(null);
    try {
      return credentials.list(ImmutableList.copyOf(kubernetesKinds), namespace);
    } catch (KubectlException e) {
      log.warn(
          "{}: Failed to read kind {} from namespace {}: {}",
          getAgentType(),
          kubernetesKinds,
          namespace,
          e.getMessage());
      throw e;
    }
  }

  @Nonnull
  private ImmutableList<KubernetesManifest> loadNamespaceScopedResources(
      @Nonnull Iterable<KubernetesKind> kubernetesKinds) {
    return getNamespaces()
        .parallelStream()
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
    return primaryKinds().stream()
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

  protected KubernetesManifest loadPrimaryResource(
      KubernetesKind kind, String namespace, String name) {
    return credentials.get(kind, namespace, name);
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info(getAgentType() + ": agent is starting");
    Map<String, Object> details = defaultIntrospectionDetails();

    try {
      long start = System.currentTimeMillis();
      Map<KubernetesKind, List<KubernetesManifest>> primaryResourceList = loadPrimaryResourceList();
      details.put("timeSpentInKubectlMs", System.currentTimeMillis() - start);
      return buildCacheResult(primaryResourceList);
    } catch (KubectlJobExecutor.NoResourceTypeException e) {
      log.warn(
          getAgentType() + ": resource for this caching agent is not supported for this cluster");
      return new DefaultCacheResult(new HashMap<>());
    }
  }

  protected CacheResult buildCacheResult(KubernetesManifest resource) {
    return buildCacheResult(
        Collections.singletonMap(resource.getKind(), Collections.singletonList(resource)));
  }

  protected CacheResult buildCacheResult(Map<KubernetesKind, List<KubernetesManifest>> resources) {
    KubernetesCacheData kubernetesCacheData = new KubernetesCacheData();
    Map<KubernetesManifest, List<KubernetesManifest>> relationships =
        loadSecondaryResourceRelationships(resources);

    resources.values().stream()
        .flatMap(Collection::stream)
        .peek(m -> RegistryUtils.removeSensitiveKeys(credentials.getResourcePropertyRegistry(), m))
        .forEach(
            rs -> {
              try {
                KubernetesCacheDataConverter.convertAsResource(
                    kubernetesCacheData,
                    accountName,
                    credentials.getKindProperties(rs.getKind()),
                    rs,
                    relationships.get(rs),
                    credentials.isOnlySpinnakerManaged());
              } catch (Exception e) {
                log.warn("{}: Failure converting {}", getAgentType(), rs, e);
              }
            });

    Map<String, Collection<CacheData>> entries = kubernetesCacheData.toStratifiedCacheData();
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
                RegistryUtils.addRelationships(
                    credentials.getResourcePropertyRegistry(), k, allResources, result);
              } catch (Exception e) {
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
}
