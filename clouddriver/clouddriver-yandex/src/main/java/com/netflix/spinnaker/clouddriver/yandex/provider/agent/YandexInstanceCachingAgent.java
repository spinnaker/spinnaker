/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.provider.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.AgentDataType.Authority;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudInstance;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudLoadBalancer;
import com.netflix.spinnaker.clouddriver.yandex.provider.Keys;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import com.netflix.spinnaker.clouddriver.yandex.service.YandexCloudFacade;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class YandexInstanceCachingAgent extends AbstractYandexCachingAgent<YandexCloudInstance> {
  private static final String TYPE = Keys.Namespace.INSTANCES.getNs();

  public YandexInstanceCachingAgent(
      YandexCloudCredentials credentials,
      ObjectMapper objectMapper,
      YandexCloudFacade yandexCloudFacade) {
    super(credentials, objectMapper, yandexCloudFacade);
  }

  public Set<AgentDataType> getProvidedDataTypes() {
    Set<AgentDataType> authoritative = new HashSet<>(super.getProvidedDataTypes());
    Collections.addAll(
        authoritative,
        Authority.INFORMATIVE.forType(Keys.Namespace.CLUSTERS.getNs()),
        Authority.INFORMATIVE.forType(Keys.Namespace.LOAD_BALANCERS.getNs()));
    return authoritative;
  }

  @Override
  protected List<YandexCloudInstance> loadEntities(ProviderCache providerCache) {
    return yandexCloudFacade.getInstances(credentials).stream()
        .peek(instance -> linkWithLoadBalancers(instance, providerCache))
        .collect(Collectors.toList());
  }

  @Override
  protected String getKey(YandexCloudInstance instance) {
    return Keys.getInstanceKey(getAccountName(), instance.getId(), getFolder(), instance.getName());
  }

  @Override
  protected String getType() {
    return TYPE;
  }

  private void linkWithLoadBalancers(YandexCloudInstance instance, ProviderCache providerCache) {
    providerCache.getAll(Keys.Namespace.LOAD_BALANCERS.getNs()).stream()
        .map(cacheData -> convert(cacheData, YandexCloudLoadBalancer.class))
        .filter(
            balancer ->
                balancer.getHealths().values().stream()
                    .flatMap(Collection::stream)
                    .anyMatch(instance::containsAddress))
        .forEach(instance::linkWithLoadBalancer);
  }

  @Override
  protected Map<String, Collection<String>> getRelationships(YandexCloudInstance instance) {
    Map<String, Collection<String>> relationships = new HashMap<>();
    String defaultName =
        Strings.isNullOrEmpty(instance.getName()) ? instance.getId() : instance.getName();
    String applicationName =
        instance.getLabels().getOrDefault("spinnaker-application", defaultName);
    String applicationKey = Keys.getApplicationKey(applicationName);
    relationships.put(
        Keys.Namespace.APPLICATIONS.getNs(), Collections.singletonList(applicationKey));

    String clusterName = instance.getLabels().getOrDefault("spinnaker-cluster", defaultName);
    String clusterKey = Keys.getClusterKey(getAccountName(), applicationName, clusterName);
    relationships.put(Keys.Namespace.CLUSTERS.getNs(), Collections.singletonList(clusterKey));
    return relationships;
  }
}
