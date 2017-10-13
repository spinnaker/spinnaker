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

import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.ClusterCacheKey;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.model.KubernetesV2Cluster;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.model.KubernetesV2LoadBalancer;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.model.KubernetesV2ServerGroup;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.LogicalKind.APPLICATION;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.LogicalKind.CLUSTER;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind.INSTANCE;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind.LOAD_BALANCER;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind.SERVER_GROUP;

@Component
@Slf4j
public class KubernetesV2ClusterProvider implements ClusterProvider<KubernetesV2Cluster> {
  private final KubernetesCacheUtils cacheUtils;
  private final KubernetesSpinnakerKindMap kindMap;

  @Autowired
  KubernetesV2ClusterProvider(KubernetesCacheUtils cacheUtils,
      KubernetesSpinnakerKindMap kindMap) {
    this.cacheUtils = cacheUtils;
    this.kindMap = kindMap;
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
    String clusterGlobKey = Keys.cluster("*", application, "*");
    return groupByAccountName(
        translateClustersWithRelationships(
            cacheUtils.getAllDataMatchingPattern(CLUSTER.toString(), clusterGlobKey))
    );
  }

  @Override
  public Set<KubernetesV2Cluster> getClusters(String application, String account) {
    String globKey = Keys.cluster(account, application, "*");
    return translateClusters(
        cacheUtils.getAllDataMatchingPattern(CLUSTER.toString(), globKey)
            .stream()
            .filter(cd -> {
              Optional<Keys.CacheKey> optionalKey = Keys.parseKey(cd.getId());
              return optionalKey.isPresent() && ((ClusterCacheKey) optionalKey.get()).getAccount().equals(account);
            })
            .collect(Collectors.toList())
    );
  }

  @Override
  public KubernetesV2Cluster getCluster(String application, String account, String name) {
    return getCluster(application, account, name, true);
  }

  @Override
  public KubernetesV2Cluster getCluster(String application, String account, String name, boolean includeDetails) {
    return cacheUtils.getSingleEntry(CLUSTER.toString(), Keys.cluster(account, application, name))
        .map(entry -> {
          Collection<CacheData> clusterData = Collections.singletonList(entry);
          Set<KubernetesV2Cluster> result = includeDetails ? translateClustersWithRelationships(clusterData) : translateClusters(clusterData);
          return result.iterator().next();
        }).orElse(null);
  }

  @Override
  public KubernetesV2ServerGroup getServerGroup(String account, String namespace, String name) {
    Triple<KubernetesApiVersion, KubernetesKind, String> parsedName;
    try {
      parsedName = KubernetesManifest.fromFullResourceName(name);
    } catch (IllegalArgumentException e) {
      return null;
    }

    KubernetesApiVersion apiVersion = parsedName.getLeft();
    KubernetesKind kind = parsedName.getMiddle();
    String shortName = parsedName.getRight();
    String key = Keys.infrastructure(apiVersion, kind, account, namespace, shortName);
    List<String> instanceGroups = kindMap.translateSpinnakerKind(INSTANCE)
        .stream()
        .map(KubernetesKind::toString)
        .collect(Collectors.toList());

    Optional<CacheData> serverGroupData = cacheUtils.getSingleEntryWithRelationships(kind.toString(),
        key,
        instanceGroups.toArray(new String[instanceGroups.size()]));

    return serverGroupData.map(cd -> {
      List<CacheData> instanceData = kindMap.translateSpinnakerKind(INSTANCE)
          .stream()
          .map(k -> cacheUtils.loadRelationshipsFromCache(Collections.singletonList(cd), k.toString()))
          .flatMap(Collection::stream)
          .collect(Collectors.toList());

      return KubernetesV2ServerGroup.fromCacheData(cd, instanceData);
    }).orElse(null);
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
    return clusterData.stream().map(this::translateCluster).filter(Objects::nonNull).collect(Collectors.toSet());
  }

  private Set<KubernetesV2Cluster> translateClustersWithRelationships(Collection<CacheData> clusterData) {
    // TODO(lwander) possible optimization: store lb relationships in cluster object to cut down on number of loads here.
    List<CacheData> serverGroupData = kindMap.translateSpinnakerKind(SERVER_GROUP)
        .stream()
        .map(kind -> cacheUtils.loadRelationshipsFromCache(clusterData, kind.toString()))
        .flatMap(Collection::stream)
        .collect(Collectors.toList());

    List<CacheData> loadBalancerData = kindMap.translateSpinnakerKind(LOAD_BALANCER)
        .stream()
        .map(kind -> cacheUtils.loadRelationshipsFromCache(serverGroupData, kind.toString()))
        .flatMap(Collection::stream)
        .collect(Collectors.toList());

    List<CacheData> instanceData = kindMap.translateSpinnakerKind(INSTANCE)
        .stream()
        .map(kind -> cacheUtils.loadRelationshipsFromCache(serverGroupData, kind.toString()))
        .flatMap(Collection::stream)
        .collect(Collectors.toList());

    Map<String, List<CacheData>> clusterToServerGroups = new HashMap<>();
    for (CacheData serverGroupDatum : serverGroupData) {
      Collection<String> clusterKeys = serverGroupDatum.getRelationships().get(CLUSTER.toString());
      if (clusterKeys == null || clusterKeys.size() != 1) {
        log.warn("Malformed cache, server group stored without cluster");
        continue;
      }

      String clusterKey = clusterKeys.iterator().next();
      List<CacheData> storedData = clusterToServerGroups.getOrDefault(clusterKey, new ArrayList<>());
      storedData.add(serverGroupDatum);
      clusterToServerGroups.put(clusterKey, storedData);
    }

    Map<String, List<CacheData>> serverGroupToLoadBalancers = new HashMap<>();
    for (CacheData loadBalancerDatum : loadBalancerData) {
      Collection<String> serverGroupKeys = cacheUtils.aggregateRelationshipsBySpinnakerKind(loadBalancerDatum, SERVER_GROUP);

      for (String serverGroupKey : serverGroupKeys) {
        List<CacheData> storedData = serverGroupToLoadBalancers.getOrDefault(serverGroupKey, new ArrayList<>());
        storedData.add(loadBalancerDatum);
        serverGroupToLoadBalancers.put(serverGroupKey, storedData);
      }
    }

    Map<String, List<CacheData>> serverGroupToInstances = new HashMap<>();
    for (CacheData instanceDatum : instanceData) {
      Collection<String> serverGroupKeys = cacheUtils.aggregateRelationshipsBySpinnakerKind(instanceDatum, SERVER_GROUP);

      for (String serverGroupKey : serverGroupKeys) {
        List<CacheData> storedData = serverGroupToInstances.getOrDefault(serverGroupKey, new ArrayList<>());
        storedData.add(instanceDatum);
        serverGroupToInstances.put(serverGroupKey, storedData);
      }
    }

    Set<KubernetesV2Cluster> result = new HashSet<>();
    for (CacheData clusterDatum : clusterData) {
      List<CacheData> clusterServerGroups = clusterToServerGroups.getOrDefault(clusterDatum.getId(), new ArrayList<>());
      List<CacheData> clusterLoadBalancers = clusterServerGroups.stream()
          .map(CacheData::getId)
          .map(id -> serverGroupToLoadBalancers.getOrDefault(id, new ArrayList<>()))
          .flatMap(Collection::stream)
          .collect(Collectors.toList());

      result.add(translateCluster(clusterDatum, clusterServerGroups, clusterLoadBalancers, serverGroupToInstances));
    }

    return result.stream()
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private KubernetesV2Cluster translateCluster(CacheData clusterDatum) {
    if (clusterDatum == null) {
      return null;
    }

    return new KubernetesV2Cluster(clusterDatum.getId());
  }

  private KubernetesV2Cluster translateCluster(CacheData clusterDatum,
      List<CacheData> serverGroupData,
      List<CacheData> loadBalancerData,
      Map<String, List<CacheData>> instanceDataByServerGroup) {
    if (clusterDatum == null) {
      return null;
    }

    List<KubernetesV2ServerGroup> serverGroups = serverGroupData.stream()
        .map(cd -> KubernetesV2ServerGroup.fromCacheData(cd, instanceDataByServerGroup.get(cd.getId())))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    List<KubernetesV2LoadBalancer> loadBalancers = loadBalancerData.stream()
        .map(KubernetesV2LoadBalancer::fromCacheData)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    return new KubernetesV2Cluster(clusterDatum.getId(), serverGroups, loadBalancers);
  }
}
