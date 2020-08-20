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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.LogicalKind.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind.INSTANCES;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind.LOAD_BALANCERS;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind.SERVER_GROUPS;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.ApplicationCacheKey;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesV2LoadBalancer;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesV2ServerGroup;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.data.KubernetesV2ServerGroupCacheData;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KubernetesV2LoadBalancerProvider
    implements LoadBalancerProvider<KubernetesV2LoadBalancer> {
  private final KubernetesCacheUtils cacheUtils;

  @Autowired
  KubernetesV2LoadBalancerProvider(KubernetesCacheUtils cacheUtils) {
    this.cacheUtils = cacheUtils;
  }

  @Override
  public String getCloudProvider() {
    return KubernetesCloudProvider.ID;
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
  public List<KubernetesV2LoadBalancer> byAccountAndRegionAndName(
      String account, String namespace, String fullName) {
    return cacheUtils
        .getSingleEntry(account, namespace, fullName)
        .map(loadBalancerData -> fromLoadBalancerCacheData(ImmutableList.of(loadBalancerData)))
        .map(ImmutableList::copyOf)
        .orElse(null);
  }

  @Override
  public Set<KubernetesV2LoadBalancer> getApplicationLoadBalancers(String application) {
    return cacheUtils
        .getSingleEntry(APPLICATIONS.toString(), ApplicationCacheKey.createKey(application))
        .map(
            applicationData ->
                fromLoadBalancerCacheData(
                    cacheUtils.getRelationships(applicationData, LOAD_BALANCERS)))
        .orElseGet(ImmutableSet::of);
  }

  private Set<KubernetesV2LoadBalancer> fromLoadBalancerCacheData(
      Collection<CacheData> loadBalancerData) {
    ImmutableMultimap<String, CacheData> loadBalancerToServerGroups =
        cacheUtils.getRelationships(loadBalancerData, SERVER_GROUPS);
    ImmutableMultimap<String, CacheData> serverGroupToInstances =
        cacheUtils.getRelationships(loadBalancerToServerGroups.values(), INSTANCES);

    return loadBalancerData.stream()
        .map(
            lb ->
                KubernetesV2LoadBalancer.fromCacheData(
                    lb,
                    loadBalancerToServerGroups.get(lb.getId()).stream()
                        .map(
                            sg ->
                                KubernetesV2ServerGroup.fromCacheData(
                                    KubernetesV2ServerGroupCacheData.builder()
                                        .serverGroupData(sg)
                                        .instanceData(serverGroupToInstances.get(sg.getId()))
                                        .loadBalancerKeys(ImmutableList.of(lb.getId()))
                                        .build()))
                        .filter(Objects::nonNull)
                        .map(KubernetesV2ServerGroup::toLoadBalancerServerGroup)
                        .collect(toImmutableSet())))
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }
}
