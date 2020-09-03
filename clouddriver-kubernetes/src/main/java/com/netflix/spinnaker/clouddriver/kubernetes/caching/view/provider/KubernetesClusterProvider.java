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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.LogicalKind.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.LogicalKind.CLUSTERS;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind.INSTANCES;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind.LOAD_BALANCERS;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind.SERVER_GROUPS;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind.SERVER_GROUP_MANAGERS;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesCluster;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesLoadBalancer;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesServerGroup;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.data.KubernetesServerGroupCacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.ServerGroupHandler;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KubernetesClusterProvider implements ClusterProvider<KubernetesCluster> {
  private final KubernetesCacheUtils cacheUtils;

  @Autowired
  KubernetesClusterProvider(KubernetesCacheUtils cacheUtils) {
    this.cacheUtils = cacheUtils;
  }

  @Override
  public Map<String, Set<KubernetesCluster>> getClusters() {
    return groupByAccountName(loadClusters(cacheUtils.getAllKeys(CLUSTERS.toString())));
  }

  @Override
  public Map<String, Set<KubernetesCluster>> getClusterSummaries(String application) {
    String applicationKey = Keys.ApplicationCacheKey.createKey(application);
    return groupByAccountName(
        loadClusterSummaries(
            cacheUtils
                .getSingleEntryWithRelationships(
                    APPLICATIONS.toString(),
                    applicationKey,
                    RelationshipCacheFilter.include(CLUSTERS.toString()))
                .map(d -> cacheUtils.getRelationships(d, CLUSTERS.toString()))
                .orElseGet(ImmutableList::of)));
  }

  @Override
  public Map<String, Set<KubernetesCluster>> getClusterDetails(String application) {
    String clusterGlobKey = Keys.ClusterCacheKey.createKey("*", application, "*");
    return groupByAccountName(
        loadClusters(cacheUtils.getAllDataMatchingPattern(CLUSTERS.toString(), clusterGlobKey)));
  }

  @Override
  public Set<KubernetesCluster> getClusters(String application, String account) {
    String globKey = Keys.ClusterCacheKey.createKey(account, application, "*");
    return loadClusters(cacheUtils.getAllDataMatchingPattern(CLUSTERS.toString(), globKey));
  }

  @Override
  public KubernetesCluster getCluster(String application, String account, String name) {
    return getCluster(application, account, name, true);
  }

  @Override
  public KubernetesCluster getCluster(
      String application, String account, String name, boolean includeDetails) {
    return cacheUtils
        .getSingleEntry(
            CLUSTERS.toString(), Keys.ClusterCacheKey.createKey(account, application, name))
        .map(
            entry -> {
              Collection<CacheData> clusterData = ImmutableList.of(entry);
              Set<KubernetesCluster> result =
                  includeDetails ? loadClusters(clusterData) : loadClusterSummaries(clusterData);
              return result.iterator().next();
            })
        .orElse(null);
  }

  @Nullable
  @Override
  public KubernetesServerGroup getServerGroup(
      String account, String namespace, String fullName, boolean includeDetails) {
    return cacheUtils
        .getSingleEntry(account, namespace, fullName)
        .map(
            serverGroupData ->
                loadServerGroups(ImmutableList.of(serverGroupData)).get(serverGroupData.getId()))
        .orElse(null);
  }

  @Override
  public KubernetesServerGroup getServerGroup(String account, String namespace, String name) {
    return getServerGroup(account, namespace, name, true);
  }

  @Override
  public String getCloudProviderId() {
    return KubernetesCloudProvider.ID;
  }

  @Override
  public boolean supportsMinimalClusters() {
    return true;
  }

  private Map<String, Set<KubernetesCluster>> groupByAccountName(
      Collection<KubernetesCluster> clusters) {
    return clusters.stream().collect(groupingBy(KubernetesCluster::getAccountName, toSet()));
  }

  private Set<KubernetesCluster> loadClusterSummaries(Collection<CacheData> clusterData) {
    return clusterData.stream()
        .map(clusterDatum -> new KubernetesCluster(clusterDatum.getId()))
        .collect(toSet());
  }

  private Set<KubernetesCluster> loadClusters(Collection<CacheData> clusterData) {
    ImmutableMultimap<String, CacheData> clusterToServerGroups =
        cacheUtils.getRelationships(clusterData, SERVER_GROUPS);

    return clusterData.stream()
        .map(
            clusterDatum -> {
              ImmutableCollection<CacheData> clusterServerGroups =
                  clusterToServerGroups.get(clusterDatum.getId());
              ImmutableMap<String, KubernetesServerGroup> serverGroups =
                  loadServerGroups(clusterServerGroups);
              List<KubernetesLoadBalancer> loadBalancers =
                  cacheUtils.getRelationships(clusterServerGroups, LOAD_BALANCERS).values().stream()
                      .filter(cacheUtils.distinctById())
                      .map(
                          cd ->
                              KubernetesLoadBalancer.fromCacheData(
                                  cd,
                                  cacheUtils.getRelationshipKeys(cd, SERVER_GROUPS).stream()
                                      .map(serverGroups::get)
                                      .filter(Objects::nonNull)
                                      .map(KubernetesServerGroup::toLoadBalancerServerGroup)
                                      .collect(toImmutableSet())))
                      .filter(Objects::nonNull)
                      .collect(Collectors.toList());

              return new KubernetesCluster(
                  clusterDatum.getId(), serverGroups.values(), loadBalancers);
            })
        .collect(toSet());
  }

  private ImmutableMap<String, KubernetesServerGroup> loadServerGroups(
      ImmutableCollection<CacheData> serverGroupData) {
    ImmutableMultimap<String, CacheData> serverGroupToInstances =
        cacheUtils.getRelationships(serverGroupData, INSTANCES);
    return serverGroupData.stream()
        .collect(
            toImmutableMap(
                CacheData::getId,
                cd ->
                    serverGroupFromCacheData(
                        KubernetesServerGroupCacheData.builder()
                            .serverGroupData(cd)
                            .instanceData(serverGroupToInstances.get(cd.getId()))
                            .loadBalancerKeys(cacheUtils.getRelationshipKeys(cd, LOAD_BALANCERS))
                            .serverGroupManagerKeys(
                                cacheUtils.getRelationshipKeys(cd, SERVER_GROUP_MANAGERS))
                            .build()),
                (sg1, sg2) -> sg1));
  }

  private final ServerGroupHandler DEFAULT_SERVER_GROUP_HANDLER = new ServerGroupHandler() {};

  @Nonnull
  private KubernetesServerGroup serverGroupFromCacheData(
      @Nonnull KubernetesServerGroupCacheData cacheData) {
    KubernetesHandler handler = cacheUtils.getHandler(cacheData);
    ServerGroupHandler serverGroupHandler =
        handler instanceof ServerGroupHandler
            ? (ServerGroupHandler) handler
            : DEFAULT_SERVER_GROUP_HANDLER;
    return serverGroupHandler.fromCacheData(cacheData);
  }
}
