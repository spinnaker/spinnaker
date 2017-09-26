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
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.KubernetesCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class KubernetesV2CachingAgent<T> extends KubernetesCachingAgent<KubernetesV2Credentials> {
  protected KubernetesV2CachingAgent(KubernetesNamedAccountCredentials<KubernetesV2Credentials> namedAccountCredentials,
      ObjectMapper objectMapper,
      Registry registry,
      int agentIndex,
      int agentCount) {
    super(namedAccountCredentials, objectMapper, registry, agentIndex, agentCount);
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    reloadNamespaces();
    return buildCacheResult(loadPrimaryResourceList());
  }

  protected CacheResult buildCacheResult(T resource) {
    return buildCacheResult(Collections.singletonList(resource));
  }

  protected CacheResult buildCacheResult(List<T> resources) {
    List<CacheData> resourceData = resources.stream()
        .map(rs -> KubernetesCacheDataConverter.convertAsResource(accountName, objectMapper, rs))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    resourceData.addAll(resources.stream()
        .map(rs -> KubernetesCacheDataConverter.convertAsArtifact(accountName, objectMapper, rs))
        .filter(Objects::nonNull)
        .collect(Collectors.toList()));

    List<CacheData> invertedRelationships = resourceData.stream()
        .map(KubernetesCacheDataConverter::invertRelationships)
        .flatMap(Collection::stream)
        .collect(Collectors.toList());

    resourceData.addAll(invertedRelationships);

    Map<String, Collection<CacheData>> entries = KubernetesCacheDataConverter.stratifyCacheDataByGroup(resourceData);
    KubernetesCacheDataConverter.logStratifiedCacheData(getAgentType(), entries);

    return new DefaultCacheResult(entries);

  }

  protected abstract List<T> loadPrimaryResourceList();
}
