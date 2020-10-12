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

package com.netflix.spinnaker.clouddriver.yandex.provider.view;

import static com.netflix.spinnaker.clouddriver.yandex.provider.Keys.Namespace.INSTANCES;
import static com.netflix.spinnaker.clouddriver.yandex.provider.Keys.Namespace.LOAD_BALANCERS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudInstance;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudLoadBalancer;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServerGroup;
import com.netflix.spinnaker.clouddriver.yandex.provider.Keys;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class YandexServerGroupProvider {
  private final CacheClient<YandexCloudServerGroup> cacheClient;

  @Autowired
  public YandexServerGroupProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheClient =
        new CacheClient<>(
            cacheView, objectMapper, Keys.Namespace.SERVER_GROUPS, YandexCloudServerGroup.class);
  }

  public List<YandexCloudServerGroup> getByApplication(String applicationName) {
    String serverGroupKey = Keys.getServerGroupKey("*", "*", "*", applicationName + "*");
    return cacheClient.findAll(serverGroupKey);
  }

  public List<YandexCloudServerGroup> getAll(Collection<String> identifiers, boolean isDetailed) {
    return identifiers.stream()
        .map(key -> new AbstractMap.SimpleEntry<>(key, cacheClient.get(key)))
        .filter(pair -> pair.getValue().isPresent())
        .map(
            pair ->
                isDetailed
                    ? fetchRelationships(pair.getKey(), pair.getValue().get())
                    : pair.getValue().get())
        .collect(Collectors.toList());
  }

  public Collection<String> getLoadBalancersKeys(String pattern) {
    Collection<String> keys = cacheClient.filterIdentifiers(pattern);
    return keys.stream()
        .map(key -> cacheClient.getRelationKeys(key, Keys.Namespace.LOAD_BALANCERS))
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  public Optional<YandexCloudServerGroup> findOne(String pattern) {
    return cacheClient.filterIdentifiers(pattern).stream()
        .map(this::fetchRelationships)
        .findFirst();
  }

  private YandexCloudServerGroup fetchRelationships(String key) {
    return cacheClient.findOne(key).map(group -> fetchRelationships(key, group)).orElse(null);
  }

  private YandexCloudServerGroup fetchRelationships(String key, YandexCloudServerGroup group) {
    group.setInstances(cacheClient.getRelationEntities(key, INSTANCES, YandexCloudInstance.class));
    updateBalancers(
        group, cacheClient.getRelationEntities(key, LOAD_BALANCERS, YandexCloudLoadBalancer.class));
    return group;
  }

  private void updateBalancers(
      YandexCloudServerGroup serverGroup, Set<YandexCloudLoadBalancer> loadBalancers) {
    if (serverGroup.getLoadBalancerIntegration() != null
        && !Strings.isNullOrEmpty(serverGroup.getLoadBalancerIntegration().getTargetGroupId())) {
      Set<String> loadBalancerIds = serverGroup.getLoadBalancersWithHealthChecks().keySet();
      Set<YandexCloudLoadBalancer> attachedBalancers =
          loadBalancers.stream()
              .filter(loadBalancer -> loadBalancerIds.contains(loadBalancer.getId()))
              .collect(Collectors.toSet());
      serverGroup.getLoadBalancerIntegration().setBalancers(attachedBalancers);

      boolean sgEnable =
          loadBalancerIds.isEmpty()
              || serverGroup.getInstances().stream()
                  .anyMatch(instance -> instance.getHealthState() == HealthState.Up);
      serverGroup.setDisabled(!sgEnable);
    }
  }
}
