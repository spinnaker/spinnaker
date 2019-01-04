/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.AgentIntervalAware;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.KubernetesCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.Kind.KUBERNETES_METRIC;

@Slf4j
public class KubernetesMetricCachingAgent extends KubernetesCachingAgent<KubernetesV2Credentials> implements AgentIntervalAware {
  @Getter
  protected String providerName = KubernetesCloudProvider.getID();

  @Getter
  private final Long agentInterval;

  @Getter
  protected Collection<AgentDataType> providedDataTypes = Collections.unmodifiableCollection(
      Collections.singletonList(AUTHORITATIVE.forType(KUBERNETES_METRIC.toString()))
  );

  protected KubernetesMetricCachingAgent(KubernetesNamedAccountCredentials<KubernetesV2Credentials> namedAccountCredentials,
      ObjectMapper objectMapper,
      Registry registry,
      int agentIndex,
      int agentCount,
      Long agentInterval) {
    super(namedAccountCredentials, objectMapper, registry, agentIndex, agentCount);
    this.agentInterval = agentInterval;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info(getAgentType() + " is starting");
    reloadNamespaces();

    List<CacheData> cacheData = namespaces.parallelStream()
        .map(n -> {
              try {
                return credentials.topPod(n)
                    .stream()
                    .map(m -> KubernetesCacheDataConverter.convertPodMetric(accountName, n, m));
              } catch (KubectlJobExecutor.KubectlException e) {
                if (e.getMessage().contains("not available")) {
                  log.warn("Metrics for namespace '" + n + "' in account '" + accountName + "' have not been recorded yet.");
                  return null;
                } else {
                  throw e;
                }
              }
            }
        ).filter(Objects::nonNull)
        .flatMap(x -> x)
        .collect(Collectors.toList());

    List<CacheData> invertedRelationships = cacheData.stream()
        .map(KubernetesCacheDataConverter::invertRelationships)
        .flatMap(Collection::stream)
        .collect(Collectors.toList());

    cacheData.addAll(invertedRelationships);

    Map<String, Collection<CacheData>> entries = KubernetesCacheDataConverter.stratifyCacheDataByGroup(KubernetesCacheDataConverter.dedupCacheData(cacheData));
    KubernetesCacheDataConverter.logStratifiedCacheData(getAgentType(), entries);

    return new DefaultCacheResult(entries);
  }
}
