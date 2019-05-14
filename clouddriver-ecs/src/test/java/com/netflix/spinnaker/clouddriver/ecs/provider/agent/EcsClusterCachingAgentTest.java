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

package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import spock.lang.Subject;

public class EcsClusterCachingAgentTest extends CommonCachingAgent {
  @Subject
  private final EcsClusterCachingAgent agent =
      new EcsClusterCachingAgent(
          netflixAmazonCredentials, REGION, clientProvider, credentialsProvider);

  @Test
  public void shouldGetListOfArns() {
    // Given
    ListClustersResult listClustersResult =
        new ListClustersResult().withClusterArns(CLUSTER_ARN_1, CLUSTER_ARN_2);
    when(ecs.listClusters(any(ListClustersRequest.class))).thenReturn(listClustersResult);

    // When
    List<String> clusterArns = agent.getItems(ecs, providerCache);

    // Then
    assertTrue(
        "Expected the list to contain 2 ECS cluster ARNs " + clusterArns.size(),
        clusterArns.size() == 2);
    assertTrue(
        "Expected the list to contain "
            + CLUSTER_ARN_1
            + ", but it does not. It contains: "
            + clusterArns,
        clusterArns.contains(CLUSTER_ARN_1));
    assertTrue(
        "Expected the list to contain "
            + CLUSTER_ARN_2
            + ", but it does not. It contains: "
            + clusterArns,
        clusterArns.contains(CLUSTER_ARN_2));
  }

  @Test
  public void shouldGenerateFreshData() {
    // Given
    Set<String> clusterArns = new HashSet<>();
    clusterArns.add(CLUSTER_ARN_1);
    clusterArns.add(CLUSTER_ARN_2);

    Set<String> keys = new HashSet<>();
    keys.add(Keys.getClusterKey(ACCOUNT, REGION, CLUSTER_NAME_1));
    keys.add(Keys.getClusterKey(ACCOUNT, REGION, CLUSTER_NAME_2));

    // When
    Map<String, Collection<CacheData>> dataMap = agent.generateFreshData(clusterArns);

    // Then
    assertTrue(
        "Expected the data map to contain 1 namespace, but it contains "
            + dataMap.keySet().size()
            + " namespaces.",
        dataMap.keySet().size() == 1);
    assertTrue(
        "Expected the data map to contain "
            + ECS_CLUSTERS.toString()
            + " namespace, but it contains "
            + dataMap.keySet()
            + " namespaces.",
        dataMap.containsKey(ECS_CLUSTERS.toString()));
    assertTrue(
        "Expected there to be 2 CacheData, instead there is  "
            + dataMap.get(ECS_CLUSTERS.toString()).size(),
        dataMap.get(ECS_CLUSTERS.toString()).size() == 2);

    for (CacheData cacheData : dataMap.get(ECS_CLUSTERS.toString())) {
      assertTrue(
          "Expected the key to be one of the following keys: "
              + keys.toString()
              + ". The key is: "
              + cacheData.getId()
              + ".",
          keys.contains(cacheData.getId()));
      assertTrue(
          "Expected the cluster ARN to be one of the following ARNs: "
              + clusterArns.toString()
              + ". The cluster ARN is: "
              + cacheData.getAttributes().get("clusterArn")
              + ".",
          clusterArns.contains(cacheData.getAttributes().get("clusterArn")));
    }
  }

  @Test
  public void shouldAddToCache() {
    // Given
    String key = Keys.getClusterKey(ACCOUNT, REGION, CLUSTER_NAME_1);
    ListClustersResult listClustersResult = new ListClustersResult().withClusterArns(CLUSTER_ARN_1);
    when(ecs.listClusters(any(ListClustersRequest.class))).thenReturn(listClustersResult);

    // When
    CacheResult cacheResult = agent.loadData(providerCache);

    // Then
    Collection<CacheData> cacheData = cacheResult.getCacheResults().get(ECS_CLUSTERS.toString());
    assertTrue("Expected CacheData to be returned but null is returned", cacheData != null);
    assertTrue("Expected 1 CacheData but returned " + cacheData.size(), cacheData.size() == 1);
    String retrievedKey = cacheData.iterator().next().getId();
    assertTrue(
        "Expected CacheData with ID " + key + " but retrieved ID " + retrievedKey,
        retrievedKey.equals(key));
  }
}
