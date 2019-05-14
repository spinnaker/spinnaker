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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.LogicalKind.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind.INSTANCES;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind.LOAD_BALANCERS;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind.SERVER_GROUPS;

import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.model.KubernetesV2LoadBalancer;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KubernetesV2LoadBalancerProvider
    implements LoadBalancerProvider<KubernetesV2LoadBalancer> {
  private final KubernetesCacheUtils cacheUtils;
  private final KubernetesSpinnakerKindMap kindMap;

  @Autowired
  KubernetesV2LoadBalancerProvider(
      KubernetesCacheUtils cacheUtils, KubernetesSpinnakerKindMap kindMap) {
    this.cacheUtils = cacheUtils;
    this.kindMap = kindMap;
  }

  @Override
  public String getCloudProvider() {
    return KubernetesCloudProvider.getID();
  }

  @Override
  public List<LoadBalancerProvider.Item> list() {
    return new ArrayList<>();
  }

  @Override
  public LoadBalancerProvider.Item get(String name) {
    throw new NotImplementedException("Not a valid operation");
  }

  @Override
  public List<LoadBalancerProvider.Details> byAccountAndRegionAndName(
      String account, String namespace, String fullName) {
    Pair<KubernetesKind, String> parsedName;
    try {
      parsedName = KubernetesManifest.fromFullResourceName(fullName);
    } catch (Exception e) {
      return null;
    }

    KubernetesKind kind = parsedName.getLeft();
    String name = parsedName.getRight();
    String key = Keys.infrastructure(kind, account, name, name);

    Optional<CacheData> optionalLoadBalancerData = cacheUtils.getSingleEntry(kind.toString(), key);
    if (!optionalLoadBalancerData.isPresent()) {
      return null;
    }

    CacheData loadBalancerData = optionalLoadBalancerData.get();

    return new ArrayList<>(fromLoadBalancerCacheData(Collections.singletonList(loadBalancerData)));
  }

  @Override
  public Set<KubernetesV2LoadBalancer> getApplicationLoadBalancers(String application) {
    List<CacheData> loadBalancerData =
        kindMap.translateSpinnakerKind(LOAD_BALANCERS).stream()
            .map(
                kind ->
                    cacheUtils.getTransitiveRelationship(
                        APPLICATIONS.toString(),
                        Collections.singletonList(Keys.application(application)),
                        kind.toString()))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    return fromLoadBalancerCacheData(loadBalancerData);
  }

  private Set<KubernetesV2LoadBalancer> fromLoadBalancerCacheData(
      List<CacheData> loadBalancerData) {
    List<CacheData> serverGroupData =
        kindMap.translateSpinnakerKind(SERVER_GROUPS).stream()
            .map(kind -> cacheUtils.loadRelationshipsFromCache(loadBalancerData, kind.toString()))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    List<CacheData> instanceData =
        kindMap.translateSpinnakerKind(INSTANCES).stream()
            .map(kind -> cacheUtils.loadRelationshipsFromCache(serverGroupData, kind.toString()))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    Map<String, List<CacheData>> loadBalancerToServerGroups =
        cacheUtils.mapByRelationship(serverGroupData, LOAD_BALANCERS);
    Map<String, List<CacheData>> serverGroupToInstances =
        cacheUtils.mapByRelationship(instanceData, SERVER_GROUPS);

    return loadBalancerData.stream()
        .map(
            cd ->
                KubernetesV2LoadBalancer.fromCacheData(
                    cd,
                    loadBalancerToServerGroups.getOrDefault(cd.getId(), new ArrayList<>()),
                    serverGroupToInstances))
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  @Data
  private class Item implements LoadBalancerProvider.Item {
    String name;
    List<ByAccount> byAccounts = new ArrayList<>();
  }
}
