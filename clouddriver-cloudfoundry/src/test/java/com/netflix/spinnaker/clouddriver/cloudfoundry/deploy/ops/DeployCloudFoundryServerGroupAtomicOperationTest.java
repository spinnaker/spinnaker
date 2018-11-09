/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.RouteId;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeployCloudFoundryServerGroupDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryDomain;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops.DeployCloudFoundryServerGroupAtomicOperation.convertToMb;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Index.atIndex;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

class DeployCloudFoundryServerGroupAtomicOperationTest extends AbstractCloudFoundryAtomicOperationTest {

  @Test
  void convertToMbHandling() {
    assertThat(convertToMb("memory", "123")).isEqualTo(123);
    assertThat(convertToMb("memory", "1G")).isEqualTo(1024);
    assertThat(convertToMb("memory", "1M")).isEqualTo(1);

    assertThatThrownBy(() -> convertToMb("memory", "abc")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> convertToMb("memory", "123.45")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void mapRoutesShouldReturnTrueWhenRoutesIsNull() {
    DefaultTask testTask = new DefaultTask("testTask");
    TaskRepository.threadLocalTask.set(testTask);
    DeployCloudFoundryServerGroupDescription description = new DeployCloudFoundryServerGroupDescription();
    DeployCloudFoundryServerGroupAtomicOperation operation = new DeployCloudFoundryServerGroupAtomicOperation(null, description, null);

    assertThat(operation.mapRoutes(null, null, null)).isTrue();
    assertThat(testTask.getHistory()).has(status("No load balancers provided to create or update"), atIndex(1));
  }

  @Test
  void mapRoutesShouldReturnTrueWhenRoutesAreValid() {
    DefaultTask testTask = new DefaultTask("testTask");
    TaskRepository.threadLocalTask.set(testTask);

    DeployCloudFoundryServerGroupDescription description = new DeployCloudFoundryServerGroupDescription();
    description.setClient(client);
    description.setServerGroupName("sg-name");
    DeployCloudFoundryServerGroupAtomicOperation operation = new DeployCloudFoundryServerGroupAtomicOperation(null, description, null);
    when(client.getRoutes().toRouteId(anyString())).thenReturn(new RouteId("road.to.nowhere", null, null, "domain-guid"));

    CloudFoundryOrganization org = CloudFoundryOrganization.builder().id("org-id").name("org-name").build();
    CloudFoundrySpace space = CloudFoundrySpace.builder().id("space-id").name("space-name").organization(org).build();
    CloudFoundryLoadBalancer loadBalancer = CloudFoundryLoadBalancer.builder().
      host("road.to").
      domain(CloudFoundryDomain.builder().
        id("domain-id").
        name("nowhere").
        organization(org).
        build()).
      build();
    when(client.getRoutes().find(any(), anyString())).thenReturn(loadBalancer);

    List<String> routeList = Collections.singletonList("road.to.nowhere");

    assertThat(operation.mapRoutes(routeList, space, null)).isTrue();
    assertThat(testTask.getHistory()).has(status("Mapping load balancer 'road.to.nowhere' to sg-name"), atIndex(2));
  }

  @Test
  void mapRoutesShouldReturnFalseWhenInvalidRoutesAreFound() {
    DefaultTask testTask = new DefaultTask("testTask");
    TaskRepository.threadLocalTask.set(testTask);

    DeployCloudFoundryServerGroupDescription description = new DeployCloudFoundryServerGroupDescription();
    description.setClient(client);
    description.setServerGroupName("sg-name");
    DeployCloudFoundryServerGroupAtomicOperation operation = new DeployCloudFoundryServerGroupAtomicOperation(null, description, null);
    when(client.getRoutes().toRouteId(anyString())).thenReturn(null);

    List<String> routeList = Collections.singletonList("road.to.nowhere");

    assertThat(operation.mapRoutes(routeList, null, null)).isFalse();
    assertThat(testTask.getHistory()).has(status("Invalid format or domain for route 'road.to.nowhere'"), atIndex(2));
  }
}
