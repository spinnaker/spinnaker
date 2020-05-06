/*
 * Copyright 2018 Google, Inc.
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

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.Kind.KUBERNETES_METRIC;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.AgentIntervalAware;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesPodMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesV2Credentials;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KubernetesMetricCachingAgent extends KubernetesV2CachingAgent
    implements AgentIntervalAware {
  @Getter protected String providerName = KubernetesCloudProvider.ID;

  @Getter
  protected ImmutableList<AgentDataType> providedDataTypes =
      ImmutableList.of(AUTHORITATIVE.forType(KUBERNETES_METRIC.toString()));

  protected KubernetesMetricCachingAgent(
      KubernetesNamedAccountCredentials<KubernetesV2Credentials> namedAccountCredentials,
      ObjectMapper objectMapper,
      Registry registry,
      int agentIndex,
      int agentCount,
      Long agentInterval) {
    super(namedAccountCredentials, objectMapper, registry, agentIndex, agentCount, agentInterval);
  }

  @Override
  protected ImmutableList<KubernetesKind> primaryKinds() {
    return ImmutableList.of();
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    if (!credentials.isMetricsEnabled()) {
      return new DefaultCacheResult(ImmutableMap.of());
    }

    log.info(getAgentType() + ": agent is starting");
    List<KubernetesPodMetric> podMetrics =
        getNamespaces()
            .parallelStream()
            .map(n -> credentials.topPod(n, null))
            .flatMap(Collection::stream)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    KubernetesCacheData kubernetesCacheData = new KubernetesCacheData();
    podMetrics.forEach(
        metric ->
            KubernetesCacheDataConverter.convertPodMetric(
                kubernetesCacheData, accountName, metric));

    Map<String, Collection<CacheData>> entries = kubernetesCacheData.toStratifiedCacheData();
    KubernetesCacheDataConverter.logStratifiedCacheData(getAgentType(), entries);

    return new DefaultCacheResult(entries);
  }
}
