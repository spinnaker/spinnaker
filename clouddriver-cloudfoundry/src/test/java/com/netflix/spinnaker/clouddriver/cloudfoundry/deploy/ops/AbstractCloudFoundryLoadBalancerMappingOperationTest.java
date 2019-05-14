/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Index.atIndex;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.MockCloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.RouteId;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.AbstractCloudFoundryServerGroupDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryDomain;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask;
import com.netflix.spinnaker.clouddriver.data.task.Status;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class AbstractCloudFoundryLoadBalancerMappingOperationTest {
  final CloudFoundryClient client;

  private DefaultTask testTask = new DefaultTask("testTask");

  {
    TaskRepository.threadLocalTask.set(testTask);
  }

  AbstractCloudFoundryLoadBalancerMappingOperationTest() {
    client = new MockCloudFoundryClient();
  }

  static Condition<? super Status> status(String desc) {
    return new Condition<>(
        status -> status.getStatus().equals(desc), "description = '" + desc + "'");
  }

  @Test
  void mapRoutesShouldReturnTrueWhenRoutesIsNull() {
    AbstractCloudFoundryServerGroupDescription description =
        new AbstractCloudFoundryServerGroupDescription() {};
    AbstractCloudFoundryAtomicOperationTestClass operation =
        new AbstractCloudFoundryAtomicOperationTestClass();

    assertThat(operation.mapRoutes(description, null, null, null)).isTrue();
    assertThat(testTask.getHistory())
        .has(status("No load balancers provided to create or update"), atIndex(1));
  }

  @Test
  void mapRoutesShouldReturnTrueWhenRoutesAreValid() {
    AbstractCloudFoundryServerGroupDescription description =
        new AbstractCloudFoundryServerGroupDescription() {};
    description.setClient(client);
    description.setServerGroupName("sg-name");
    AbstractCloudFoundryAtomicOperationTestClass operation =
        new AbstractCloudFoundryAtomicOperationTestClass();
    when(client.getRoutes().toRouteId(anyString()))
        .thenReturn(new RouteId("road.to.nowhere", null, null, "domain-guid"));

    CloudFoundryOrganization org =
        CloudFoundryOrganization.builder().id("org-id").name("org-name").build();
    CloudFoundrySpace space =
        CloudFoundrySpace.builder().id("space-id").name("space-name").organization(org).build();
    CloudFoundryLoadBalancer loadBalancer =
        CloudFoundryLoadBalancer.builder()
            .host("road.to")
            .domain(
                CloudFoundryDomain.builder()
                    .id("domain-id")
                    .name("nowhere")
                    .organization(org)
                    .build())
            .build();
    when(client.getRoutes().createRoute(any(RouteId.class), anyString())).thenReturn(loadBalancer);

    List<String> routeList = Collections.singletonList("road.to.nowhere");

    assertThat(operation.mapRoutes(description, routeList, space, null)).isTrue();
    assertThat(testTask.getHistory())
        .has(status("Mapping load balancer 'road.to.nowhere' to sg-name"), atIndex(2));
  }

  @Test
  void mapRoutesShouldThrowAnExceptionWhenInvalidRoutesAreFound() {
    AbstractCloudFoundryServerGroupDescription description =
        new AbstractCloudFoundryServerGroupDescription() {};
    description.setClient(client);
    description.setServerGroupName("sg-name");
    AbstractCloudFoundryAtomicOperationTestClass operation =
        new AbstractCloudFoundryAtomicOperationTestClass();
    when(client.getRoutes().toRouteId(anyString())).thenReturn(null);

    List<String> routeList = Collections.singletonList("road.to.nowhere");

    Exception exception = null;
    try {
      operation.mapRoutes(description, routeList, null, null);
    } catch (IllegalArgumentException illegalArgumentException) {
      exception = illegalArgumentException;
    }
    assertThat(exception).isNotNull();
    assertThat(exception.getMessage()).isEqualTo("road.to.nowhere is an invalid route");
  }

  @Test
  void mapRoutesShouldThrowAnExceptionWhenRoutesExistInOtherOrgSpace() {
    AbstractCloudFoundryServerGroupDescription description =
        new AbstractCloudFoundryServerGroupDescription() {};
    description.setClient(client);
    description.setServerGroupName("sg-name");

    CloudFoundryOrganization org =
        CloudFoundryOrganization.builder().id("org-id").name("org-name").build();
    CloudFoundrySpace space =
        CloudFoundrySpace.builder().id("space-id").name("space-name").organization(org).build();

    AbstractCloudFoundryAtomicOperationTestClass operation =
        new AbstractCloudFoundryAtomicOperationTestClass();
    when(client.getRoutes().toRouteId(anyString()))
        .thenReturn(new RouteId("road.to.nowhere", null, null, "domain-guid"));
    when(client.getRoutes().createRoute(any(RouteId.class), anyString())).thenReturn(null);

    List<String> routeList = Collections.singletonList("road.to.nowhere");

    Exception exception = null;
    try {
      operation.mapRoutes(description, routeList, space, null);
    } catch (CloudFoundryApiException cloudFoundryApiException) {
      exception = cloudFoundryApiException;
    }
    assertThat(exception).isNotNull();
    assertThat(exception.getMessage())
        .isEqualTo(
            "Cloud Foundry API returned with error(s): Load balancer already exists in another organization and space");
  }

  private static class AbstractCloudFoundryAtomicOperationTestClass
      extends AbstractCloudFoundryLoadBalancerMappingOperation {
    @Override
    protected String getPhase() {
      return "IT_S_JUST_A_PHASE";
    }
  }
}
