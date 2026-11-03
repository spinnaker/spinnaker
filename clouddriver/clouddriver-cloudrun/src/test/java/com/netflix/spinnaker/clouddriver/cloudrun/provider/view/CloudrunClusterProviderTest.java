/*
 * Copyright 2026 Harness, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunCluster;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunServerGroup;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CloudrunClusterProviderTest {
  private CloudrunClusterProvider clusterProvider;
  private CloudrunLoadBalancerProvider loadBalancerProvider;

  @BeforeEach
  void setUp() {
    clusterProvider = new CloudrunClusterProvider();
    loadBalancerProvider = mock(CloudrunLoadBalancerProvider.class);
    clusterProvider.provider = loadBalancerProvider;
  }

  @Test
  void missingLoadBalancerDoesNotFailAndLeavesTagsUnset() throws Exception {
    CloudrunServerGroup serverGroup = serverGroup("my-service-00001");
    when(loadBalancerProvider.getLoadBalancer("my-account", "my-service")).thenReturn(null);

    addLatestRevision(cluster(serverGroup));

    assertThat(serverGroup.getTags()).isNullOrEmpty();
  }

  @Test
  void tagsServerGroupWithLatestRevision() throws Exception {
    CloudrunServerGroup latest = serverGroup("my-service-00002");
    CloudrunServerGroup older = serverGroup("my-service-00001");
    CloudrunLoadBalancer loadBalancer = new CloudrunLoadBalancer();
    loadBalancer.setLatestReadyRevisionName("my-service-00002");
    when(loadBalancerProvider.getLoadBalancer("my-account", "my-service")).thenReturn(loadBalancer);

    addLatestRevision(cluster(latest, older));

    assertThat(latest.getTags()).containsEntry("isLatest", true);
    assertThat(older.getTags()).containsEntry("isLatest", false);
    assertThat(older.getTags()).containsEntry("latestRevision", "my-service-00002");
  }

  private static CloudrunServerGroup serverGroup(String name) {
    CloudrunServerGroup serverGroup = new CloudrunServerGroup();
    serverGroup.setName(name);
    serverGroup.setAccount("my-account");
    serverGroup.getLoadBalancers().add("my-service");
    return serverGroup;
  }

  private static CloudrunCluster cluster(CloudrunServerGroup... serverGroups) {
    CloudrunCluster cluster = new CloudrunCluster();
    cluster.setServerGroups(Set.of(serverGroups));
    return cluster;
  }

  private void addLatestRevision(CloudrunCluster cluster) throws Exception {
    Method method =
        CloudrunClusterProvider.class.getDeclaredMethod(
            "addLatestRevisionToServerGroup", Set.class);
    method.setAccessible(true);
    method.invoke(clusterProvider, Set.of(cluster));
  }
}
