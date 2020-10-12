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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.yandex.YandexCloudProvider;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexApplication;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudCluster;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServerGroup;
import com.netflix.spinnaker.clouddriver.yandex.provider.Keys;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class YandexClusterProvider implements ClusterProvider<YandexCloudCluster> {
  private final CacheClient<YandexCloudCluster> cacheClient;
  private final YandexApplicationProvider applicationProvider;
  private final YandexServerGroupProvider serverGroupProvider;
  private AccountCredentialsProvider accountCredentialsProvider;

  @Autowired
  public YandexClusterProvider(
      Cache cacheView,
      ObjectMapper objectMapper,
      YandexApplicationProvider applicationProvider,
      YandexServerGroupProvider serverGroupProvider,
      AccountCredentialsProvider accountCredentialsProvider) {
    this.applicationProvider = applicationProvider;
    this.serverGroupProvider = serverGroupProvider;
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.cacheClient =
        new CacheClient<>(
            cacheView, objectMapper, Keys.Namespace.CLUSTERS, YandexCloudCluster.class);
  }

  @Override
  public String getCloudProviderId() {
    return YandexCloudProvider.ID;
  }

  @Override
  public Map<String, Set<YandexCloudCluster>> getClusters() {
    return cacheClient.getAll(Keys.CLUSTER_WILDCARD).stream()
        .collect(Collectors.groupingBy(YandexCloudCluster::getAccountName, Collectors.toSet()));
  }

  @Override
  public Map<String, Set<YandexCloudCluster>> getClusterDetails(String applicationName) {
    return getClusters(applicationName, true);
  }

  @Override
  public Map<String, Set<YandexCloudCluster>> getClusterSummaries(String applicationName) {
    return getClusters(applicationName, false);
  }

  @Override
  public Set<YandexCloudCluster> getClusters(String applicationName, String account) {
    return getClusterDetails(applicationName).get(account);
  }

  @Override
  public YandexCloudCluster getCluster(
      String application, String account, String name, boolean isDetailed) {
    String clusterKey = Keys.getClusterKey(account, application, name);
    return cacheClient
        .findOne(clusterKey)
        .map(cluster -> buildCluster(clusterKey, cluster, isDetailed))
        .orElse(null);
  }

  @Override
  public YandexCloudCluster getCluster(
      String applicationName, String accountName, String clusterName) {
    return getCluster(applicationName, accountName, clusterName, true);
  }

  @Override
  public YandexCloudServerGroup getServerGroup(
      String account, String region, String name, boolean includeDetails) {
    AccountCredentials<?> credentials = accountCredentialsProvider.getCredentials(account);
    if (!(credentials instanceof YandexCloudCredentials)) {
      return null;
    }
    String pattern =
        Keys.getServerGroupKey(
            account, "*", ((YandexCloudCredentials) credentials).getFolder(), name);
    return serverGroupProvider.findOne(pattern).orElse(null);
  }

  @Override
  public YandexCloudServerGroup getServerGroup(String account, String region, String name) {
    return getServerGroup(account, region, name, true);
  }

  @Override
  public boolean supportsMinimalClusters() {
    return false;
  }

  private Map<String, Set<YandexCloudCluster>> getClusters(
      String applicationName, boolean isDetailed) {
    YandexApplication application = applicationProvider.getApplication(applicationName);
    String applicationKey = Keys.getApplicationKey(applicationName);

    if (application == null) {
      return new HashMap<>();
    }

    Collection<String> clusterKeys =
        applicationProvider.getRelationship(applicationKey, Keys.Namespace.CLUSTERS);
    List<YandexCloudCluster> clusters = cacheClient.getAll(clusterKeys);

    return clusters.stream()
        .map(
            cluster ->
                buildCluster(
                    Keys.getClusterKey(
                        cluster.getAccountName(), applicationName, cluster.getName()),
                    cluster,
                    isDetailed))
        .collect(Collectors.groupingBy(YandexCloudCluster::getAccountName, Collectors.toSet()));
  }

  private YandexCloudCluster buildCluster(
      String key, YandexCloudCluster cluster, boolean isDetailed) {
    Collection<String> serverGroupKeys =
        cacheClient.getRelationKeys(key, Keys.Namespace.SERVER_GROUPS);
    if (serverGroupKeys.isEmpty()) {
      return cluster;
    }
    List<YandexCloudServerGroup> groups = serverGroupProvider.getAll(serverGroupKeys, isDetailed);
    groups.forEach(
        group -> {
          cluster.getServerGroups().add(group);
          Optional.ofNullable(group.getLoadBalancerIntegration())
              .map(YandexCloudServerGroup.LoadBalancerIntegration::getBalancers)
              .ifPresent(lbs -> cluster.getLoadBalancers().addAll(lbs));
        });
    return cluster;
  }
}
