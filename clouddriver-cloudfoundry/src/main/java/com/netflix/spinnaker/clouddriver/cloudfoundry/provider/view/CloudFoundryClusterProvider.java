/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.provider.view;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys.Namespace.CLUSTERS;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryCloudProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.CacheRepository;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryCluster;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServerGroup;
import com.netflix.spinnaker.clouddriver.model.Cluster;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class CloudFoundryClusterProvider implements ClusterProvider {
  private final Cache cacheView;
  private final CacheRepository repository;

  @Override
  public String getCloudProviderId() {
    return CloudFoundryCloudProvider.ID;
  }

  private static Map<String, Set<CloudFoundryCluster>> distinctGroupByAccount(
      Collection<CloudFoundryCluster> clusters) {
    return clusters.stream().collect(groupingBy(Cluster::getAccountName, toSet()));
  }

  @Override
  public Map<String, Set<CloudFoundryCluster>> getClusters() {
    return distinctGroupByAccount(
        repository.findClustersByKeys(
            cacheView.filterIdentifiers(CLUSTERS.getNs(), Keys.getClusterKey("*", "*", "*")),
            CacheRepository.Detail.FULL));
  }

  @Override
  public Map<String, Set<CloudFoundryCluster>> getClusterSummaries(String applicationName) {
    return distinctGroupByAccount(
        repository.findClustersByKeys(
            cacheView.filterIdentifiers(
                CLUSTERS.getNs(), Keys.getClusterKey("*", applicationName, "*")),
            CacheRepository.Detail.NAMES_ONLY));
  }

  @Override
  public Map<String, Set<CloudFoundryCluster>> getClusterDetails(String applicationName) {
    return distinctGroupByAccount(
        repository.findClustersByKeys(
            cacheView.filterIdentifiers(
                CLUSTERS.getNs(), Keys.getClusterKey("*", applicationName, "*")),
            CacheRepository.Detail.FULL));
  }

  @Override
  public Set<CloudFoundryCluster> getClusters(String applicationName, String account) {
    return repository.findClustersByKeys(
        cacheView.filterIdentifiers(
            CLUSTERS.getNs(), Keys.getClusterKey(account, applicationName, "*")),
        CacheRepository.Detail.FULL);
  }

  @Nullable
  @Override
  public CloudFoundryCluster getCluster(
      String applicationName, String account, String clusterName) {
    return getCluster(applicationName, account, clusterName, true);
  }

  @Nullable
  @Override
  public CloudFoundryCluster getCluster(
      String application, String account, String name, boolean includeDetails) {
    return repository
        .findClusterByKey(
            Keys.getClusterKey(account, application, name),
            includeDetails ? CacheRepository.Detail.FULL : CacheRepository.Detail.NAMES_ONLY)
        .orElse(null);
  }

  @Nullable
  @Override
  public CloudFoundryServerGroup getServerGroup(
      String account, String region, String name, boolean includeDetails) {
    return repository
        .findServerGroupByKey(
            Keys.getServerGroupKey(account, name, region),
            includeDetails ? CacheRepository.Detail.FULL : CacheRepository.Detail.NAMES_ONLY)
        .orElse(null);
  }

  @Override
  public CloudFoundryServerGroup getServerGroup(String account, String region, String name) {
    return getServerGroup(account, region, name, true);
  }

  @Override
  public boolean supportsMinimalClusters() {
    return true;
  }
}
