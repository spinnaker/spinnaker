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

package com.netflix.spinnaker.clouddriver.ecs.cache;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsClusterCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsCluster;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.EcsClusterCachingAgent;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import spock.lang.Subject;

public class EcsClusterCacheClientTest extends CommonCacheClient {
  @Subject private final EcsClusterCacheClient client = new EcsClusterCacheClient(cacheView);

  @Test
  public void shouldConvert() {
    // Given
    String clusterName = "test-cluster";
    String clusterArn = "arn:aws:ecs:" + REGION + ":012345678910:cluster/" + clusterName;
    String key = Keys.getClusterKey(ACCOUNT, REGION, clusterName);

    Map<String, Object> attributes =
        EcsClusterCachingAgent.convertClusterArnToAttributes(ACCOUNT, REGION, clusterArn);

    when(cacheView.get(ECS_CLUSTERS.toString(), key))
        .thenReturn(new DefaultCacheData(key, attributes, Collections.emptyMap()));

    // When
    EcsCluster ecsCluster = client.get(key);

    // Then
    assertTrue(
        "Expected cluster name to be " + clusterName + " but got " + ecsCluster.getName(),
        clusterName.equals(ecsCluster.getName()));
    assertTrue(
        "Expected cluster ARN to be " + clusterArn + " but got " + ecsCluster.getArn(),
        clusterArn.equals(ecsCluster.getArn()));
    assertTrue(
        "Expected cluster account to be " + ACCOUNT + " but got " + ecsCluster.getAccount(),
        ACCOUNT.equals(ecsCluster.getAccount()));
    assertTrue(
        "Expected cluster region to be " + REGION + " but got " + ecsCluster.getRegion(),
        REGION.equals(ecsCluster.getRegion()));
  }
}
