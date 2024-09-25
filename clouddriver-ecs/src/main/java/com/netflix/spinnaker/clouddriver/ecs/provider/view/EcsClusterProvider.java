/*
 * Copyright 2017 Lookout, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.ecs.provider.view;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.Cluster;
import com.amazonaws.services.ecs.model.DescribeClustersRequest;
import com.amazonaws.services.ecs.model.DescribeClustersResult;
import com.google.common.collect.Lists;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsClusterCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsCluster;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EcsClusterProvider {

  private EcsClusterCacheClient ecsClusterCacheClient;
  @Autowired private CredentialsRepository<NetflixECSCredentials> credentialsRepository;
  @Autowired private AmazonClientProvider amazonClientProvider;
  // Describe Cluster API accepts only 100 cluster Names at a time as an input.
  private static final int EcsClusterDescriptionMaxSize = 100;

  @Autowired
  public EcsClusterProvider(Cache cacheView) {
    this.ecsClusterCacheClient = new EcsClusterCacheClient(cacheView);
  }

  public Collection<EcsCluster> getAllEcsClusters() {
    return ecsClusterCacheClient.getAll();
  }

  // TODO include[] input of Describe Cluster is not a part of this implementation, need to
  // implement in the future if additional properties are needed.
  public Collection<Cluster> getEcsClusterDescriptions(String account, String region) {
    String glob = Keys.getClusterKey(account, region, "*");
    Collection<String> ecsClustersIdentifiers = ecsClusterCacheClient.filterIdentifiers(glob);
    Collection<Cluster> clusters = new ArrayList<>();
    List<String> filteredEcsClusters =
        ecsClusterCacheClient.getAll(ecsClustersIdentifiers).stream()
            .filter(
                cluster ->
                    account.equals(cluster.getAccount()) && region.equals(cluster.getRegion()))
            .map(cluster -> cluster.getName())
            .collect(Collectors.toList());
    log.info("Total number of items in the filteredEcsCluster(s): {}", filteredEcsClusters.size());
    List<List<String>> batchClusterList =
        Lists.partition(filteredEcsClusters, EcsClusterDescriptionMaxSize);
    log.info("filteredEcsCluster(s) item(s) split among {} partition(s)", batchClusterList.size());
    AmazonECS client = getAmazonEcsClient(account, region);
    for (List<String> batchClusters : batchClusterList) {
      List<Cluster> describeClusterResponse = getDescribeClusters(client, batchClusters);
      if (describeClusterResponse != null) {
        clusters.addAll(describeClusterResponse);
      }
    }
    return clusters;
  }

  private AmazonECS getAmazonEcsClient(String account, String region) {
    NetflixECSCredentials credentials = credentialsRepository.getOne(account);
    if (!(credentials instanceof NetflixECSCredentials)) {
      throw new IllegalArgumentException("Invalid credentials:" + account + ":" + region);
    }
    return amazonClientProvider.getAmazonEcs(credentials, region, true);
  }

  private List<Cluster> getDescribeClusters(AmazonECS client, List<String> clusterNames) {
    DescribeClustersRequest describeClustersRequest =
        new DescribeClustersRequest().withClusters(clusterNames);
    DescribeClustersResult describeClustersResult =
        client.describeClusters(describeClustersRequest);
    if (describeClustersResult == null) {
      log.warn(
          "Describe Cluster call returned with empty response. Please check your inputs (account, region and cluster list)");
      return Collections.emptyList();
    } else if (!describeClustersResult.getFailures().isEmpty()) {
      log.warn(
          "Describe Cluster call responded with failure(s):"
              + describeClustersResult.getFailures());
    }
    return describeClustersResult.getClusters();
  }
}
