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

import static com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops.DeployCloudFoundryServerGroupAtomicOperation.convertToMb;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.clouddriver.cloudfoundry.artifacts.ArtifactCredentialsFromString;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.AbstractServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Resource;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.ServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.CreatePackage;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Docker;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ProcessStats;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeployCloudFoundryServerGroupDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServerGroup;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.view.CloudFoundryClusterProvider;
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import io.vavr.collection.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.mockito.verification.VerificationMode;

class DeployCloudFoundryServerGroupAtomicOperationTest
    extends AbstractCloudFoundryAtomicOperationTest {

  private final CloudFoundryClient cloudFoundryClient = new MockCloudFoundryClient();

  private DefaultTask testTask = new DefaultTask("testTask");

  {
    TaskRepository.threadLocalTask.set(testTask);
  }

  @Test
  void convertToMbHandling() {
    assertThat(convertToMb("memory", "123")).isEqualTo(123);
    assertThat(convertToMb("memory", "1G")).isEqualTo(1024);
    assertThat(convertToMb("memory", "1M")).isEqualTo(1);

    assertThatThrownBy(() -> convertToMb("memory", "abc"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> convertToMb("memory", "123.45"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void executeOperationAndDeploySucceeds() {
    // Given
    final DeployCloudFoundryServerGroupDescription description =
        getDeployCloudFoundryServerGroupDescription(true);
    final CloudFoundryClusterProvider clusterProvider = mock(CloudFoundryClusterProvider.class);
    final DeployCloudFoundryServerGroupAtomicOperation operation =
        new DeployCloudFoundryServerGroupAtomicOperation(
            new PassThroughOperationPoller(), description);
    final Applications apps = getApplications(clusterProvider, ProcessStats.State.RUNNING);
    final ServiceInstances serviceInstances = getServiceInstances();

    // When
    final DeploymentResult result = operation.operate(Lists.emptyList());

    // Then
    verifyInOrder(apps, serviceInstances, () -> atLeastOnce());

    assertThat(testTask.getStatus().isFailed()).isFalse();
    assertThat(result.getServerGroupNames())
        .isEqualTo(Collections.singletonList("region1:app1-stack1-detail1-v000"));
  }

  @Test
  void executeOperationAndDeployDockerSucceeds() {
    // Given
    final DeployCloudFoundryServerGroupDescription description =
        getDockerDeployCloudFoundryServerGroupDescription(true);
    final CloudFoundryClusterProvider clusterProvider = mock(CloudFoundryClusterProvider.class);
    final DeployCloudFoundryServerGroupAtomicOperation operation =
        new DeployCloudFoundryServerGroupAtomicOperation(
            new PassThroughOperationPoller(), description);
    final Applications apps = getApplications(clusterProvider, ProcessStats.State.RUNNING);
    final ServiceInstances serviceInstances = getServiceInstances();

    // When
    final DeploymentResult result = operation.operate(Lists.emptyList());

    // Then
    verifyInOrderDockerDeploy(apps, serviceInstances, () -> atLeastOnce());

    assertThat(testTask.getStatus().isFailed()).isFalse();
    assertThat(result.getServerGroupNames())
        .isEqualTo(Collections.singletonList("region1:app1-stack1-detail1-v000"));
  }

  @Test
  void executeOperationAndDeployFails() {
    // Given
    final DeployCloudFoundryServerGroupDescription description =
        getDeployCloudFoundryServerGroupDescription(true);
    final CloudFoundryClusterProvider clusterProvider = mock(CloudFoundryClusterProvider.class);
    final DeployCloudFoundryServerGroupAtomicOperation operation =
        new DeployCloudFoundryServerGroupAtomicOperation(
            new PassThroughOperationPoller(), description);
    final Applications apps = getApplications(clusterProvider, ProcessStats.State.CRASHED);

    Exception exception = null;
    // When
    try {
      when(description
              .getClient()
              .getServiceInstances()
              .findAllServicesBySpaceAndNames(any(), any()))
          .thenReturn(createServiceInstanceResource());
      operation.operate(Lists.emptyList());
    } catch (CloudFoundryApiException cloudFoundryApiException) {
      exception = cloudFoundryApiException;
    }

    // Then
    assertThat(exception).isNotNull();
    assertThat(exception.getMessage())
        .isEqualTo(
            "Cloud Foundry API returned with error(s): Failed to start 'app1-stack1-detail1-v000' which instead crashed");
  }

  @Test
  void executeOperationWithNoStartFlag() {
    // Given
    final DeployCloudFoundryServerGroupDescription description =
        getDeployCloudFoundryServerGroupDescription(false);
    final CloudFoundryClusterProvider clusterProvider = mock(CloudFoundryClusterProvider.class);
    final DeployCloudFoundryServerGroupAtomicOperation operation =
        new DeployCloudFoundryServerGroupAtomicOperation(
            new PassThroughOperationPoller(), description);
    final Applications apps = getApplications(clusterProvider, ProcessStats.State.RUNNING);
    final ServiceInstances serviceInstances = getServiceInstances();

    // When
    final DeploymentResult result = operation.operate(Lists.emptyList());

    // Then
    verifyInOrder(apps, serviceInstances, () -> never());

    assertThat(testTask.getStatus().isFailed()).isFalse();
    assertThat(result.getServerGroupNames())
        .isEqualTo(Collections.singletonList("region1:app1-stack1-detail1-v000"));
  }

  private void verifyInOrder(
      final Applications apps,
      ServiceInstances serviceInstances,
      Supplier<VerificationMode> calls) {
    InOrder inOrder = Mockito.inOrder(apps, serviceInstances);
    inOrder.verify(apps).createApplication(any(), any(), any(), any());
    inOrder
        .verify(apps)
        .createPackage(eq(new CreatePackage("serverGroupId", CreatePackage.Type.BITS, null)));
    inOrder.verify(apps).uploadPackageBits(any(), any());
    inOrder.verify(cloudFoundryClient.getServiceInstances()).createServiceBinding(any());
    inOrder.verify(apps).createBuild(any());
    inOrder.verify(apps).scaleApplication("serverGroupId", 7, 1024, 2048);
    inOrder.verify(apps).updateProcess("serverGroupId", null, "http", "/health");
    inOrder.verify(apps, calls.get()).startApplication("serverGroupId");
  }

  private void verifyInOrderDockerDeploy(
      final Applications apps,
      ServiceInstances serviceInstances,
      Supplier<VerificationMode> calls) {
    InOrder inOrder = Mockito.inOrder(apps, serviceInstances);
    inOrder.verify(apps).createApplication(any(), any(), any(), any());
    inOrder.verify(apps).createPackage(any());
    inOrder.verify(cloudFoundryClient.getServiceInstances()).createServiceBinding(any());
    inOrder.verify(apps).createBuild(any());
    inOrder.verify(apps).scaleApplication("serverGroupId", 7, 1024, 2048);
    inOrder.verify(apps).updateProcess("serverGroupId", null, "http", "/health");
    inOrder.verify(apps, calls.get()).startApplication("serverGroupId");
  }

  private ServiceInstances getServiceInstances() {
    final ServiceInstances serviceInstances = cloudFoundryClient.getServiceInstances();
    when(serviceInstances.findAllServicesBySpaceAndNames(any(), any()))
        .thenReturn(createServiceInstanceResource());
    return serviceInstances;
  }

  private List<Resource<? extends AbstractServiceInstance>> createServiceInstanceResource() {
    ServiceInstance serviceInstance = new ServiceInstance();
    serviceInstance.setServicePlanGuid("plan-guid").setName("service1");
    Resource<ServiceInstance> serviceInstanceResource = new Resource<>();
    serviceInstanceResource.setMetadata(new Resource.Metadata().setGuid("service-instance-guid"));
    serviceInstanceResource.setEntity(serviceInstance);
    return List.of(serviceInstanceResource);
  }

  private Applications getApplications(
      CloudFoundryClusterProvider clusterProvider, ProcessStats.State state) {
    final Applications apps = cloudFoundryClient.getApplications();
    when(clusterProvider.getClusters()).thenReturn(Collections.emptyMap());
    when(apps.createApplication(any(), any(), any(), any()))
        .thenReturn(
            CloudFoundryServerGroup.builder()
                .id("serverGroupId")
                .space(CloudFoundrySpace.builder().id("spaceId").build())
                .build());
    when(apps.getProcessState(any())).thenReturn(state);
    when(apps.createPackage(any()))
        .thenAnswer(
            (Answer<String>)
                invocation -> {
                  Object[] args = invocation.getArguments();
                  return args[0].toString() + "_package";
                });
    return apps;
  }

  private DeployCloudFoundryServerGroupDescription getDeployCloudFoundryServerGroupDescription(
      boolean b) {
    final DeployCloudFoundryServerGroupDescription description =
        new DeployCloudFoundryServerGroupDescription()
            .setAccountName("account1")
            .setApplication("app1")
            .setStack("stack1")
            .setFreeFormDetails("detail1")
            .setArtifactCredentials(
                new ArtifactCredentialsFromString(
                    "test", io.vavr.collection.List.of("a").asJava(), ""))
            .setSpace(CloudFoundrySpace.builder().id("space1Id").name("space1").build())
            .setApplicationArtifact(Artifact.builder().reference("ref1").build())
            .setDocker(null)
            .setApplicationAttributes(
                new DeployCloudFoundryServerGroupDescription.ApplicationAttributes()
                    .setInstances(7)
                    .setMemory("1G")
                    .setDiskQuota("2048M")
                    .setHealthCheckType("http")
                    .setHealthCheckHttpEndpoint("/health")
                    .setBuildpacks(io.vavr.collection.List.of("buildpack1", "buildpack2").asJava())
                    .setServices(List.of("service1"))
                    .setEnv(HashMap.of("token", "ASDF").toJavaMap()));
    description.setClient(cloudFoundryClient);
    description.setRegion("region1");
    description.setStartApplication(b);
    return description;
  }

  private DeployCloudFoundryServerGroupDescription
      getDockerDeployCloudFoundryServerGroupDescription(boolean b) {
    final DeployCloudFoundryServerGroupDescription description =
        new DeployCloudFoundryServerGroupDescription()
            .setAccountName("account1")
            .setApplication("app1")
            .setStack("stack1")
            .setFreeFormDetails("detail1")
            .setSpace(CloudFoundrySpace.builder().id("space1Id").name("space1").build())
            .setArtifactCredentials(
                new ArtifactCredentialsFromString(
                    "test", io.vavr.collection.List.of("a").asJava(), ""))
            .setApplicationArtifact(Artifact.builder().reference("ref1").build())
            .setDocker(Docker.builder().image("some/image").build())
            .setApplicationAttributes(
                new DeployCloudFoundryServerGroupDescription.ApplicationAttributes()
                    .setInstances(7)
                    .setMemory("1G")
                    .setDiskQuota("2048M")
                    .setHealthCheckType("http")
                    .setHealthCheckHttpEndpoint("/health")
                    .setBuildpacks(Collections.emptyList())
                    .setServices(List.of("service1"))
                    .setEnv(HashMap.of("token", "ASDF").toJavaMap()));
    description.setClient(cloudFoundryClient);
    description.setRegion("region1");
    description.setStartApplication(b);
    return description;
  }
}
