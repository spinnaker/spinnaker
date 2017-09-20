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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.view.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.ClusterCacheKey;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.view.model.KubernetesV2Cluster;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.view.model.KubernetesV2ServerGroup;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.LogicalKind.APPLICATION;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.LogicalKind.CLUSTER;

@Component
public class KubernetesV2ClusterProvider implements ClusterProvider<KubernetesV2Cluster> {
  private final KubernetesCloudProvider kubernetesCloudProvider;
  private final Cache cache;
  private final KubernetesCacheUtils cacheUtils;
  private final ObjectMapper objectMapper;

  @Autowired
  KubernetesV2ClusterProvider(KubernetesCloudProvider kubernetesCloudProvider,
      Cache cache,
      ObjectMapper objectMapper) {
    this.kubernetesCloudProvider = kubernetesCloudProvider;
    this.cache = cache;
    this.cacheUtils = new KubernetesCacheUtils(cache);
    this.objectMapper = objectMapper;
  }

  @Override
  public Map<String, Set<KubernetesV2Cluster>> getClusters() {
    return groupByAccountName(
        translateClusters(cacheUtils.getAllKeys(CLUSTER.toString()))
    );
  }

  @Override
  public Map<String, Set<KubernetesV2Cluster>> getClusterSummaries(String application) {
    String applicationKey = Keys.application(application);
    return groupByAccountName(
        translateClusters(cacheUtils.getTransitiveRelationship(APPLICATION.toString(),
            Collections.singletonList(applicationKey),
            CLUSTER.toString()))
    );
  }

  @Override
  public Map<String, Set<KubernetesV2Cluster>> getClusterDetails(String application) {
    // TODO(lwander) provide summary/detail distinction
    return getClusterSummaries(application);
  }

  @Override
  public Set<KubernetesV2Cluster> getClusters(String application, String account) {
    String applicationKey = Keys.application(application);
    return translateClusters(
        cacheUtils.getTransitiveRelationship(APPLICATION.toString(),
            Collections.singletonList(applicationKey),
            CLUSTER.toString()
        ).stream()
        .filter(cd -> {
          Optional<Keys.CacheKey> optionalKey = Keys.parseKey(cd.getId());
          return optionalKey.isPresent() && ((ClusterCacheKey) optionalKey.get()).getAccount().equals(account);
        }).collect(Collectors.toList())
    );
  }

  @Override
  public KubernetesV2Cluster getCluster(String application, String account, String name) {
    return getCluster(application, account, name, true);
  }

  @Override
  public KubernetesV2Cluster getCluster(String application, String account, String name, boolean includeDetails) {
    return translateCluster(cacheUtils.getSingleEntry(CLUSTER.toString(), Keys.cluster(account, name)));
  }

  @Override
  public KubernetesV2ServerGroup getServerGroup(String account, String region, String name) {
    // TODO(lwander)
    return null;
  }

  @Override
  public String getCloudProviderId() {
    return KubernetesCloudProvider.getID();
  }

  @Override
  public boolean supportsMinimalClusters() {
    return false;
  }

  private Map<String, Set<KubernetesV2Cluster>> groupByAccountName(Collection<KubernetesV2Cluster> clusters) {
    Map<String, Set<KubernetesV2Cluster>> result = new HashMap<>();
    for (KubernetesV2Cluster cluster : clusters) {
      String accountName = cluster.getAccountName();
      Set<KubernetesV2Cluster> grouping = result.get(accountName);
      if (grouping == null) {
        grouping = new HashSet<>();
      }

      grouping.add(cluster);
      result.put(accountName, grouping);
    }

    return result;
  }

  private Set<KubernetesV2Cluster> translateClusters(Collection<CacheData> clusterData) {
    // TODO(lwander) resolve server group & lb relationships
    return clusterData.stream().map(this::translateCluster).filter(Objects::nonNull).collect(Collectors.toSet());
  }

  private KubernetesV2Cluster translateCluster(CacheData clusterDatum) {
    if (clusterDatum == null) {
      return null;
    }

    Optional<Keys.CacheKey> optionalKey = Keys.parseKey(clusterDatum.getId());
    if (!optionalKey.isPresent()) {
      return null;
    }

    ClusterCacheKey clusterCacheKey = (ClusterCacheKey) optionalKey.get();
    return new KubernetesV2Cluster(clusterCacheKey);
  }
}
