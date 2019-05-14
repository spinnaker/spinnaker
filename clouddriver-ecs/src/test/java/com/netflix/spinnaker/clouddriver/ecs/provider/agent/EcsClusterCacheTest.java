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
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsClusterCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsCluster;
import java.util.Collection;
import org.junit.Assert;
import org.junit.Test;
import spock.lang.Subject;

public class EcsClusterCacheTest extends CommonCachingAgent {
  @Subject
  private final EcsClusterCachingAgent agent =
      new EcsClusterCachingAgent(
          netflixAmazonCredentials, REGION, clientProvider, credentialsProvider);

  @Subject private final EcsClusterCacheClient client = new EcsClusterCacheClient(providerCache);

  @Test
  public void shouldRetrieveFromWrittenCache() {
    // Given
    String key = Keys.getClusterKey(ACCOUNT, REGION, CLUSTER_NAME_1);
    ListClustersResult listClustersResult = new ListClustersResult().withClusterArns(CLUSTER_ARN_1);
    when(ecs.listClusters(any(ListClustersRequest.class))).thenReturn(listClustersResult);

    // When
    CacheResult cacheResult = agent.loadData(providerCache);
    when(providerCache.get(ECS_CLUSTERS.toString(), key))
        .thenReturn(cacheResult.getCacheResults().get(ECS_CLUSTERS.toString()).iterator().next());

    // Then
    Collection<CacheData> cacheData = cacheResult.getCacheResults().get(ECS_CLUSTERS.toString());
    EcsCluster ecsCluster = client.get(key);

    assertTrue("Expected CacheData to be returned but null is returned", cacheData != null);
    assertTrue("Expected 1 CacheData but returned " + cacheData.size(), cacheData.size() == 1);
    String retrievedKey = cacheData.iterator().next().getId();
    assertTrue(
        "Expected CacheData with ID " + key + " but retrieved ID " + retrievedKey,
        retrievedKey.equals(key));

    Assert.assertTrue(
        "Expected cluster name to be " + CLUSTER_NAME_1 + " but got " + ecsCluster.getName(),
        CLUSTER_NAME_1.equals(ecsCluster.getName()));
    Assert.assertTrue(
        "Expected cluster ARN to be " + CLUSTER_ARN_1 + " but got " + ecsCluster.getArn(),
        CLUSTER_ARN_1.equals(ecsCluster.getArn()));
    Assert.assertTrue(
        "Expected cluster account to be " + ACCOUNT + " but got " + ecsCluster.getAccount(),
        ACCOUNT.equals(ecsCluster.getAccount()));
    Assert.assertTrue(
        "Expected cluster region to be " + REGION + " but got " + ecsCluster.getRegion(),
        REGION.equals(ecsCluster.getRegion()));
  }
}
