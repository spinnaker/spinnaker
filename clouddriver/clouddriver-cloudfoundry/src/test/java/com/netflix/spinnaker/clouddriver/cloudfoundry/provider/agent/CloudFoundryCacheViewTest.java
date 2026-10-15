/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.provider.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryApplication;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryCluster;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryDomain;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServerGroup;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CloudFoundryCacheViewTest {
  private final CloudFoundrySpace space =
      CloudFoundrySpace.builder()
          .id("space-id")
          .name("space")
          .organization(CloudFoundryOrganization.builder().id("org-id").name("org").build())
          .build();
  private final CloudFoundryInstance instance =
      CloudFoundryInstance.builder()
          .appGuid("server-group-id")
          .key("0")
          .healthState(HealthState.Up)
          .build();
  private final CloudFoundryServerGroup serverGroup =
      CloudFoundryServerGroup.builder()
          .id("server-group-id")
          .name("app-stack-v001")
          .account("account")
          .memory(1024)
          .space(space)
          .state(CloudFoundryServerGroup.State.STARTED)
          .instances(Set.of(instance))
          .loadBalancerNames(Set.of("route.example.com"))
          .build();
  private final CloudFoundryCluster cluster =
      CloudFoundryCluster.builder()
          .accountName("account")
          .name("app-stack")
          .serverGroups(Set.of(serverGroup))
          .build();

  @Test
  void applicationExcludesRelationshipsAndDerivedProperties() {
    CloudFoundryApplication application =
        CloudFoundryApplication.builder().name("app").clusters(Set.of(cluster)).build();

    assertThat(projection(application)).isEqualTo(Map.of("name", "app"));
  }

  @Test
  void clusterExcludesServerGroupsAndDerivedProperties() {
    assertThat(projection(cluster))
        .isEqualTo(Map.of("accountName", "account", "name", "app-stack"));
  }

  @Test
  void serverGroupRetainsCacheFieldsAndExcludesRelationshipsAndDerivedProperties() {
    assertThat(projection(serverGroup))
        .containsEntry("id", "server-group-id")
        .containsEntry("account", "account")
        .containsEntry("name", "app-stack-v001")
        .containsEntry("memory", 1024)
        .containsEntry("state", "STARTED")
        .containsEntry("space", projection(space))
        .doesNotContainKeys(
            "instances",
            "loadBalancerNames",
            "loadBalancers",
            "imagesSummary",
            "imageSummary",
            "image",
            "buildInfo",
            "region",
            "disabled",
            "zones",
            "securityGroups",
            "launchConfig",
            "instanceCounts",
            "capacity",
            "stack",
            "detail",
            "moniker",
            "type",
            "cloudProvider");
  }

  @Test
  void instanceExcludesDerivedProperties() {
    assertThat(projection(instance))
        .containsEntry("appGuid", "server-group-id")
        .containsEntry("key", "0")
        .containsEntry("healthState", "Up")
        .doesNotContainKeys("health", "providerType", "id", "name", "cloudProvider");
  }

  @Test
  void loadBalancerExcludesMappedAppsAndDerivedServerGroups() {
    CloudFoundryLoadBalancer loadBalancer =
        CloudFoundryLoadBalancer.builder()
            .account("account")
            .id("route-id")
            .host("route")
            .space(space)
            .domain(CloudFoundryDomain.builder().id("domain-id").name("example.com").build())
            .mappedApps(Set.of(serverGroup))
            .build();

    assertThat(projection(loadBalancer))
        .containsEntry("account", "account")
        .containsEntry("id", "route-id")
        .containsEntry("host", "route")
        .containsEntry("space", projection(space))
        .doesNotContainKeys(
            "mappedApps", "serverGroups", "name", "moniker", "type", "region", "cloudProvider");
  }

  @Test
  void spaceRetainsOrganizationAndExcludesDerivedRegion() {
    assertThat(projection(space))
        .isEqualTo(
            Map.of(
                "id", "space-id",
                "name", "space",
                "organization", Map.of("id", "org-id", "name", "org")));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> projection(Object resource) {
    return (Map<String, Object>) AbstractCloudFoundryCachingAgent.cacheView(resource).get("resource");
  }
}
