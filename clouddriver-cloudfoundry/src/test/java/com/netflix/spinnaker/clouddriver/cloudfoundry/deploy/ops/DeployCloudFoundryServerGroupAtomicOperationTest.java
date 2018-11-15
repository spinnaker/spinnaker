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

import com.netflix.spinnaker.clouddriver.cloudfoundry.artifacts.ArtifactCredentialsFromString;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.Applications;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.MockCloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.RouteId;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ProcessStats;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeployCloudFoundryServerGroupDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.view.CloudFoundryClusterProvider;
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import io.vavr.collection.HashMap;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.util.Collections;
import java.util.List;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops.DeployCloudFoundryServerGroupAtomicOperation.convertToMb;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Index.atIndex;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeployCloudFoundryServerGroupAtomicOperationTest extends AbstractCloudFoundryAtomicOperationTest {

  private final CloudFoundryClient cloudFoundryClient = new MockCloudFoundryClient();

  DefaultTask testTask = new DefaultTask("testTask");
  {
    TaskRepository.threadLocalTask.set(testTask);
  }

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
    DeployCloudFoundryServerGroupDescription description = new DeployCloudFoundryServerGroupDescription();
    DeployCloudFoundryServerGroupAtomicOperation operation = new DeployCloudFoundryServerGroupAtomicOperation(null, description, null);

    assertThat(operation.mapRoutes(null, null, null)).isTrue();
    assertThat(testTask.getHistory()).has(status("No load balancers provided to create or update"), atIndex(1));
  }

  @Test
  void mapRoutesShouldReturnTrueWhenRoutesAreValid() {
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
    DeployCloudFoundryServerGroupDescription description = new DeployCloudFoundryServerGroupDescription();
    description.setClient(client);
    description.setServerGroupName("sg-name");
    DeployCloudFoundryServerGroupAtomicOperation operation = new DeployCloudFoundryServerGroupAtomicOperation(null, description, null);
    when(client.getRoutes().toRouteId(anyString())).thenReturn(null);

    List<String> routeList = Collections.singletonList("road.to.nowhere");

    assertThat(operation.mapRoutes(routeList, null, null)).isFalse();
    assertThat(testTask.getHistory()).has(status("Invalid format or domain for route 'road.to.nowhere'"), atIndex(2));
  }

  @Test
  void executeOperation() {
    // Given
    final DeployCloudFoundryServerGroupDescription description = new DeployCloudFoundryServerGroupDescription()
      .setAccountName("account1")
      .setApplication("app1")
      .setStack("stack1")
      .setDetail("detail1")
      .setArtifactCredentials(new ArtifactCredentialsFromString(
        "test",
        io.vavr.collection.List.of("a").asJava(),
        ""
      ))
      .setSpace(CloudFoundrySpace.builder().id("space1Id").name("space1").build())
      .setArtifact(Artifact.builder().reference("ref1").build())
      .setApplicationAttributes(new DeployCloudFoundryServerGroupDescription.ApplicationAttributes()
        .setInstances(7)
        .setMemory("1G")
        .setDiskQuota("2048M")
        .setBuildpacks(io.vavr.collection.List.of("buildpack1", "buildpack2").asJava())
        .setServices(io.vavr.collection.List.of("service1").asJava())
        .setEnv(HashMap.of(
          "token", "ASDF"
        ).toJavaMap()));
    description.setClient(cloudFoundryClient);
    description.setRegion("region1");
    final CloudFoundryClusterProvider clusterProvider = mock(CloudFoundryClusterProvider.class);
    final DeployCloudFoundryServerGroupAtomicOperation operation =
      new DeployCloudFoundryServerGroupAtomicOperation(new PassThroughOperationPoller(), description, clusterProvider);

    final Applications apps = cloudFoundryClient.getApplications();
    when(clusterProvider.getClusters()).thenReturn(Collections.emptyMap());
    when(apps.createApplication(any(), any(), any(), any()))
      .thenReturn(CloudFoundryServerGroup.builder().id("serverGroupId").space(
        CloudFoundrySpace.builder().id("spaceId").build()
      ).build());
    when(apps.getProcessState(any())).thenReturn(ProcessStats.State.RUNNING);
    when(apps.createPackage(any()))
      .thenAnswer((Answer<String>) invocation -> {
        Object[] args = invocation.getArguments();
        return args[0].toString() + "_package";
      });

    // When
    final DeploymentResult result = operation.operate(Lists.emptyList());

    // Then
    final InOrder inOrder = Mockito.inOrder(apps, cloudFoundryClient.getServiceInstances());
    inOrder.verify(apps).createApplication("app1-stack1-detail1-v000",
      CloudFoundrySpace.builder().id("space1Id").name("space1").build(),
      io.vavr.collection.List.of("buildpack1", "buildpack2").asJava(),
      HashMap.of(
        "token", "ASDF"
      ).toJavaMap());
    inOrder.verify(apps).uploadPackageBits(eq("serverGroupId_package"), any());
    inOrder.verify(apps).createBuild("serverGroupId_package");
    inOrder.verify(apps).scaleApplication("serverGroupId", 7, 1024, 2048);
    inOrder.verify(cloudFoundryClient.getServiceInstances()).createServiceBindingsByName(any(), eq(Collections.singletonList("service1")));
    inOrder.verify(apps).startApplication("serverGroupId");

    assertThat(result.getServerGroupNames()).isEqualTo(Collections.singletonList("region1:app1-stack1-detail1-v000"));
  }
}
