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

package com.netflix.spinnaker.clouddriver.cloudfoundry.cache;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.cache.CacheRepository.Detail.FULL;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.cache.CacheRepository.Detail.NAMES_ONLY;
import static java.util.Collections.*;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyCollectionOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.mem.InMemoryCache;
import com.netflix.spinnaker.cats.provider.DefaultProviderCache;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.Applications;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.Routes;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.agent.CloudFoundryCachingAgent;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CacheRepositoryTest {
  private final ProviderCache cache = new DefaultProviderCache(new InMemoryCache());
  private final CacheRepository repo = new CacheRepository(cache);

  @BeforeEach
  void before() {
    CloudFoundryInstance instance =
        CloudFoundryInstance.builder()
            .appGuid("appguid")
            .key("abc123")
            .healthState(HealthState.Up)
            .launchTime(1L)
            .zone("us-east-1")
            .build();

    CloudFoundryServerGroup serverGroup =
        CloudFoundryServerGroup.builder()
            .name("demo-dev-v001")
            .account("devaccount")
            .createdTime(1L)
            .space(CloudFoundrySpace.fromRegion("myorg > dev"))
            .instances(singleton(instance))
            .droplet(
                CloudFoundryDroplet.builder()
                    .id("dropletid")
                    .name("dropletname")
                    .buildpacks(
                        singletonList(
                            CloudFoundryBuildpack.builder().buildpackName("java").build()))
                    .sourcePackage(CloudFoundryPackage.builder().checksum("check").build())
                    .build())
            .build();

    CloudFoundryCluster cluster =
        CloudFoundryCluster.builder()
            .accountName("devaccount")
            .name("demo-dev")
            .serverGroups(singleton(serverGroup))
            .build();
    CloudFoundryApplication app =
        CloudFoundryApplication.builder().name("demo").clusters(singleton(cluster)).build();

    CloudFoundryClient client = mock(CloudFoundryClient.class);
    Applications apps = mock(Applications.class);
    Routes routes = mock(Routes.class);
    ProviderCache providerCache = mock(ProviderCache.class);

    when(client.getApplications()).thenReturn(apps);
    when(client.getRoutes()).thenReturn(routes);
    when(apps.all()).thenReturn(singletonList(app));
    when(routes.all()).thenReturn(emptyList());
    when(providerCache.filterIdentifiers(any(), any())).thenReturn(emptyList());
    when(providerCache.getAll(any(), anyCollectionOf(String.class))).thenReturn(emptyList());

    CloudFoundryCachingAgent agent =
        new CloudFoundryCachingAgent("devaccount", client, mock(Registry.class));

    CacheResult result = agent.loadData(providerCache);
    List<String> authoritativeTypes =
        agent.getProvidedDataTypes().stream().map(AgentDataType::getTypeName).collect(toList());
    cache.putCacheResult(agent.getAgentType(), authoritativeTypes, result);
  }

  @Test
  void findApplication() {
    assertThat(repo.findApplicationByKey(Keys.getApplicationKey("demo"), FULL))
        .hasValueSatisfying(
            app -> {
              assertThat(app.getName()).isEqualTo("demo");
              assertThat(app.getClusters())
                  .hasOnlyOneElementSatisfying(
                      cluster -> {
                        assertThat(cluster.getName()).isEqualTo("demo-dev");

                        // rehydrated clusters are shallow, serve only the purpose of providing
                        // cluster names
                        assertThat(cluster.getServerGroups()).isEmpty();
                      });
            });
  }

  @Test
  void findCluster() {
    String clusterKey = Keys.getClusterKey("devaccount", "demo", "demo-dev");

    assertThat(repo.findClusterByKey(clusterKey, FULL))
        .hasValueSatisfying(
            cluster -> {
              assertThat(cluster.getName()).isEqualTo("demo-dev");
              assertThat(cluster.getServerGroups())
                  .hasOnlyOneElementSatisfying(
                      serverGroup -> assertThat(serverGroup.getInstances()).hasSize(1));
            });

    assertThat(repo.findClusterByKey(clusterKey, NAMES_ONLY))
        .hasValueSatisfying(
            cluster ->
                assertThat(cluster.getServerGroups())
                    .hasOnlyOneElementSatisfying(
                        serverGroup -> {
                          assertThat(serverGroup.getLoadBalancers()).isEmpty();
                          assertThat(serverGroup.getInstances()).isNotEmpty();
                        }));
  }

  @Test
  void findServerGroup() {
    assertThat(
            repo.findServerGroupByKey(
                Keys.getServerGroupKey("devaccount", "demo-dev-v001", "myorg > dev"), FULL))
        .hasValueSatisfying(
            serverGroup -> {
              assertThat(serverGroup.getName()).isEqualTo("demo-dev-v001");
              assertThat(serverGroup.getInstances())
                  .hasOnlyOneElementSatisfying(
                      inst -> {
                        assertThat(inst.getHealthState()).isEqualTo(HealthState.Up);
                        assertThat(inst.getZone()).isEqualTo("us-east-1");
                        assertThat(inst.getLaunchTime()).isEqualTo(1L);
                      });
            });
  }
}
