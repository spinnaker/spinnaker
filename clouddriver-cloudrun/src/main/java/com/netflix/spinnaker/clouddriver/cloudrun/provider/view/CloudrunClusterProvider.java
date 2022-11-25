/*
 * Copyright 2022 OpsMx, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.provider.view;

import static com.netflix.spinnaker.clouddriver.cloudrun.cache.Keys.Namespace.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunCloudProvider;
import com.netflix.spinnaker.clouddriver.cloudrun.cache.Keys;
import com.netflix.spinnaker.clouddriver.cloudrun.model.*;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class CloudrunClusterProvider implements ClusterProvider<CloudrunCluster> {

  @Autowired private Cache cacheView;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private CloudrunApplicationProvider cloudrunApplicationProvider;

  @Autowired CloudrunLoadBalancerProvider provider;

  @Override
  public Set<CloudrunCluster> getClusters(String applicationName, final String account) {
    CacheData application =
        cacheView.get(
            APPLICATIONS.getNs(),
            Keys.getApplicationKey(applicationName),
            RelationshipCacheFilter.include(CLUSTERS.getNs()));

    if (application == null) {
      return new HashSet<CloudrunCluster>();
    }
    Collection<String> clusterKeys =
        application.getRelationships().get(CLUSTERS.getNs()).stream()
            .filter(s -> Objects.requireNonNull(Keys.parse(s)).get("account").equals(account))
            .collect(Collectors.toList());
    Collection<CacheData> clusterData = cacheView.getAll(CLUSTERS.getNs(), clusterKeys);
    return (Set<CloudrunCluster>) translateClusters(clusterData, true);
  }

  @Override
  public Map<String, Set<CloudrunCluster>> getClusters() {
    Collection<CacheData> clusterData = cacheView.getAll(CLUSTERS.getNs());
    Map<String, List<CloudrunCluster>> mapClusterList =
        translateClusters(clusterData, true).stream()
            .collect(Collectors.groupingBy(CloudrunCluster::getName));
    Map<String, Set<CloudrunCluster>> mapClusterSet = new HashMap<>();
    mapClusterList.forEach((k, v) -> mapClusterSet.put(k, new HashSet<>(v)));
    return mapClusterSet;
  }

  @Override
  public CloudrunCluster getCluster(
      String application, String account, String name, boolean includeDetails) {
    if (cacheView.get(CLUSTERS.getNs(), Keys.getClusterKey(account, application, name)) != null) {
      List<CacheData> clusterData =
          List.of(cacheView.get(CLUSTERS.getNs(), Keys.getClusterKey(account, application, name)));
      if (clusterData != null) {
        Optional<CloudrunCluster> cluster =
            translateClusters(clusterData, includeDetails).stream().findFirst();
        if (cluster.isPresent()) {
          return cluster.get();
        }
      }
    }
    return null;
  }

  @Override
  public CloudrunCluster getCluster(String applicationName, String account, String clusterName) {
    return getCluster(applicationName, account, clusterName, true);
  }

  @Override
  public CloudrunServerGroup getServerGroup(
      String account, String region, String serverGroupName, boolean includeDetails) {
    String serverGroupKey = Keys.getServerGroupKey(account, serverGroupName, region);
    CacheData serverGroupData = cacheView.get(SERVER_GROUPS.getNs(), serverGroupKey);
    if (serverGroupData == null) {
      return null;
    }
    Set<CloudrunInstance> instances =
        cacheView
            .getAll(INSTANCES.getNs(), serverGroupData.getRelationships().get(INSTANCES.getNs()))
            .stream()
            .map(s -> CloudrunProviderUtils.instanceFromCacheData(objectMapper, s))
            .collect(Collectors.toSet());
    return CloudrunProviderUtils.serverGroupFromCacheData(objectMapper, serverGroupData, instances);
  }

  @Override
  public CloudrunServerGroup getServerGroup(String account, String region, String serverGroupName) {
    return getServerGroup(account, region, serverGroupName, true);
  }

  @Override
  public Map<String, Set<CloudrunCluster>> getClusterSummaries(String applicationName) {
    Map<String, List<CloudrunCluster>> mapClusterList =
        translateClusters(getClusterData(applicationName), false).stream()
            .collect(Collectors.groupingBy(CloudrunCluster::getName));
    Map<String, Set<CloudrunCluster>> mapClusterSet = new HashMap<>();
    mapClusterList.forEach((k, v) -> mapClusterSet.put(k, new HashSet<>(v)));
    return mapClusterSet;
  }

  @Override
  public Map<String, Set<CloudrunCluster>> getClusterDetails(String applicationName) {
    Map<String, List<CloudrunCluster>> mapClusterList =
        translateClusters(getClusterData(applicationName), true).stream()
            .collect(Collectors.groupingBy(CloudrunCluster::getName));
    Map<String, Set<CloudrunCluster>> mapClusterSet = new HashMap<>();
    mapClusterList.forEach((k, v) -> mapClusterSet.put(k, new HashSet<>(v)));
    return mapClusterSet;
  }

  public Set<CacheData> getClusterData(final String applicationName) {
    CloudrunApplication application = cloudrunApplicationProvider.getApplication(applicationName);
    List<String> clusterKeys = new ArrayList<>();
    // TODO - handle null
    assert application != null;
    if (application != null && application.getClusterNames() != null) {
      application
          .getClusterNames()
          .forEach(
              (accountName, clusterNames) ->
                  clusterKeys.addAll(
                      clusterNames.stream()
                          .map(
                              clusterName ->
                                  Keys.getClusterKey(accountName, applicationName, clusterName))
                          .collect(Collectors.toSet())));
    }
    Collection<CacheData> data =
        cacheView.getAll(
            CLUSTERS.getNs(),
            clusterKeys,
            RelationshipCacheFilter.include(SERVER_GROUPS.getNs(), LOAD_BALANCERS.getNs()));

    if (CollectionUtils.isEmpty(data)) {
      return Collections.emptySet();
    }
    return data.stream().filter(it -> it != null).collect(Collectors.toSet());
  }

  @Override
  public String getCloudProviderId() {
    return CloudrunCloudProvider.ID;
  }

  @Override
  public boolean supportsMinimalClusters() {
    return false;
  }

  public Set<CloudrunCluster> translateClusters(
      Collection<CacheData> clusterData, boolean includeDetails) {
    if (clusterData == null) {
      return new HashSet<>();
    }

    Map<String, CloudrunLoadBalancer> loadBalancers =
        includeDetails
            ? translateLoadBalancers(
                CloudrunProviderUtils.resolveRelationshipDataForCollection(
                    cacheView, clusterData, LOAD_BALANCERS.getNs()))
            : null;

    Map<String, Set<CloudrunServerGroup>> serverGroups =
        includeDetails
            ? translateServerGroups(
                CloudrunProviderUtils.resolveRelationshipDataForCollection(
                    cacheView,
                    clusterData,
                    SERVER_GROUPS.getNs(),
                    RelationshipCacheFilter.include(INSTANCES.getNs(), LOAD_BALANCERS.getNs())))
            : null;

    Set<CloudrunCluster> clusters = new HashSet<>();
    for (CacheData clusterDataEntry : clusterData) {
      Map<String, String> clusterKey = Keys.parse(clusterDataEntry.getId());
      assert clusterKey != null;
      CloudrunCluster cluster =
          new CloudrunCluster()
              .setAccountName(clusterKey.get("account"))
              .setName(clusterKey.get("name"));

      if (includeDetails) {
        cluster.setLoadBalancers(
            clusterDataEntry.getRelationships().get(LOAD_BALANCERS.getNs()).stream()
                .map(loadBalancers::get)
                .collect(Collectors.toSet()));

        cluster.setServerGroups(
            serverGroups.get(cluster.getName()).stream()
                .filter(it -> it.getAccount().equals(cluster.getAccountName()))
                .collect(Collectors.toSet()));
      } else {
        clusterDataEntry
            .getRelationships()
            .get(LOAD_BALANCERS.getNs())
            .forEach(
                loadBalancerKey -> {
                  Map<String, String> parts = Keys.parse(loadBalancerKey);
                  assert parts != null;
                  cluster
                      .getLoadBalancers()
                      .add(
                          new CloudrunLoadBalancer()
                              .setName(parts.get("name"))
                              .setAccount(parts.get("account")));
                });

        clusterDataEntry
            .getRelationships()
            .get(SERVER_GROUPS.getNs())
            .forEach(
                serverGroupKey -> {
                  Map<String, String> parts = Keys.parse(serverGroupKey);
                  assert parts != null;
                  cluster
                      .getServerGroups()
                      .add(
                          new CloudrunServerGroup()
                              .setName(parts.get("name"))
                              .setAccount(parts.get("account"))
                              .setRegion(parts.get("region")));
                });
      }
      clusters.add(cluster);
    }
    addLatestRevisionToServerGroup(clusters);
    return clusters;
  }

  private void addLatestRevisionToServerGroup(Set<CloudrunCluster> clusters) {
    clusters.forEach(
        cluster -> {
          Set<CloudrunServerGroup> serverGroups = cluster.getServerGroups();
          serverGroups.forEach(
              serverGroup -> {
                serverGroup
                    .getLoadBalancers()
                    .forEach(
                        name -> {
                          CloudrunLoadBalancer loadBalancer =
                              provider.getLoadBalancer(serverGroup.getAccount(), name);
                          Map<String, Object> tags = new HashMap<>();
                          tags.put("latestRevision", loadBalancer.getLatestReadyRevisionName());
                          if (serverGroup
                              .getName()
                              .equals(loadBalancer.getLatestReadyRevisionName())) {
                            tags.put("isLatest", true);
                          } else {
                            tags.put("isLatest", false);
                          }
                          serverGroup.setTags(tags);
                        });
              });
        });
  }

  public Map<String, Set<CloudrunServerGroup>> translateServerGroups(
      Collection<CacheData> serverGroupData) {
    Map<String, Collection<CacheData>> instanceCacheDataMap =
        CloudrunProviderUtils.preserveRelationshipDataForCollection(
            cacheView, serverGroupData, INSTANCES.getNs(), RelationshipCacheFilter.none());
    Map<String, Set<CloudrunInstance>> instances = new HashMap<>();
    instanceCacheDataMap.forEach(
        (k, v) -> {
          instances.put(
              k,
              v.stream()
                  .map(c -> CloudrunProviderUtils.instanceFromCacheData(objectMapper, c))
                  .collect(Collectors.toSet()));
        });

    Map<String, Set<CloudrunServerGroup>> acc = new HashMap<>();
    serverGroupData.forEach(
        cacheData -> {
          CloudrunServerGroup serverGroup =
              CloudrunProviderUtils.serverGroupFromCacheData(
                  objectMapper, cacheData, instances.get(cacheData.getId()));
          String clusterName = Names.parseName(serverGroup.getName()).getCluster();
          if (acc.isEmpty() || !acc.containsKey(clusterName)) {
            acc.put(
                clusterName,
                new HashSet<>() {
                  {
                    add(serverGroup);
                  }
                });
          } else {
            acc.get(clusterName).add(serverGroup);
          }
        });
    return acc;
  }

  public static Map<String, CloudrunLoadBalancer> translateLoadBalancers(
      Collection<CacheData> loadBalancerData) {
    Map<String, CloudrunLoadBalancer> result = new HashMap<>();
    loadBalancerData.forEach(
        loadBalancerEntry -> {
          Map<String, String> parts = Keys.parse(loadBalancerEntry.getId());
          // TODO - handle nulls
          assert parts != null;
          result.put(
              loadBalancerEntry.getId(),
              new CloudrunLoadBalancer()
                  .setName(parts.get("name"))
                  .setAccount(parts.get("account")));
        });
    return result;
  }

  public Cache getCacheView() {
    return cacheView;
  }

  public void setCacheView(Cache cacheView) {
    this.cacheView = cacheView;
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public void setObjectMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public CloudrunApplicationProvider getCloudrunApplicationProvider() {
    return cloudrunApplicationProvider;
  }

  public void setCloudrunApplicationProvider(
      CloudrunApplicationProvider CloudrunApplicationProvider) {
    this.cloudrunApplicationProvider = CloudrunApplicationProvider;
  }
}
