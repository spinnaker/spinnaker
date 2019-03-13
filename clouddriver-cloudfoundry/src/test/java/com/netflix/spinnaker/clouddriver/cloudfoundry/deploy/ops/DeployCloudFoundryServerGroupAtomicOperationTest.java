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
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.MockCloudFoundryClient;
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
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.mockito.verification.VerificationMode;

import java.util.Collections;
import java.util.function.Supplier;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops.DeployCloudFoundryServerGroupAtomicOperation.convertToMb;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

class DeployCloudFoundryServerGroupAtomicOperationTest extends AbstractCloudFoundryAtomicOperationTest {

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

    assertThatThrownBy(() -> convertToMb("memory", "abc")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> convertToMb("memory", "123.45")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void executeOperationAndDeploySucceeds() {
    // Given
    final DeployCloudFoundryServerGroupDescription description = getDeployCloudFoundryServerGroupDescription(true);
    final CloudFoundryClusterProvider clusterProvider = mock(CloudFoundryClusterProvider.class);
    final DeployCloudFoundryServerGroupAtomicOperation operation =
      new DeployCloudFoundryServerGroupAtomicOperation(new PassThroughOperationPoller(), description, clusterProvider);
    final Applications apps = getApplications(clusterProvider, ProcessStats.State.RUNNING);

    // When
    final DeploymentResult result = operation.operate(Lists.emptyList());

    // Then
    verifyInOrder(apps, () -> atLeastOnce());

    assertThat(testTask.getStatus().isFailed()).isFalse();
    assertThat(result.getServerGroupNames()).isEqualTo(Collections.singletonList("region1:app1-stack1-detail1-v000"));
  }

  @Test
  void executeOperationAndDeployFails() {
    // Given
    final DeployCloudFoundryServerGroupDescription description = getDeployCloudFoundryServerGroupDescription(true);
    final CloudFoundryClusterProvider clusterProvider = mock(CloudFoundryClusterProvider.class);
    final DeployCloudFoundryServerGroupAtomicOperation operation =
      new DeployCloudFoundryServerGroupAtomicOperation(new PassThroughOperationPoller(), description, clusterProvider);
    final Applications apps = getApplications(clusterProvider, ProcessStats.State.CRASHED);

    Exception exception = null;
    // When
    try {
      operation.operate(Lists.emptyList());
    } catch (CloudFoundryApiException cloudFoundryApiException) {
      exception = cloudFoundryApiException;
    }

    //Then
    assertThat(exception).isNotNull();
    assertThat(exception.getMessage()).isEqualTo("Cloud Foundry API returned with error(s): Failed to start 'app1-stack1-detail1-v000' which instead crashed");
  }

  @Test
  void executeOperationWithNoStartFlag() {
    // Given
    final DeployCloudFoundryServerGroupDescription description = getDeployCloudFoundryServerGroupDescription(false);
    final CloudFoundryClusterProvider clusterProvider = mock(CloudFoundryClusterProvider.class);
    final DeployCloudFoundryServerGroupAtomicOperation operation =
      new DeployCloudFoundryServerGroupAtomicOperation(new PassThroughOperationPoller(), description, clusterProvider);
    final Applications apps = getApplications(clusterProvider, ProcessStats.State.RUNNING);

    // When
    final DeploymentResult result = operation.operate(Lists.emptyList());

    // Then
    verifyInOrder(apps, () -> never());

    assertThat(testTask.getStatus().isFailed()).isFalse();
    assertThat(result.getServerGroupNames()).isEqualTo(Collections.singletonList("region1:app1-stack1-detail1-v000"));
  }

  private void verifyInOrder(final Applications apps, Supplier<VerificationMode> calls) {
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
    inOrder.verify(apps).updateProcess("serverGroupId", null, "http", "/health");
    inOrder.verify(cloudFoundryClient.getServiceInstances()).createServiceBindingsByName(any(), eq(Collections.singletonList("service1")));
    inOrder.verify(apps, calls.get()).startApplication("serverGroupId");
  }

  private Applications getApplications(CloudFoundryClusterProvider clusterProvider, ProcessStats.State state) {
    final Applications apps = cloudFoundryClient.getApplications();
    when(clusterProvider.getClusters()).thenReturn(Collections.emptyMap());
    when(apps.createApplication(any(), any(), any(), any()))
      .thenReturn(CloudFoundryServerGroup.builder().id("serverGroupId").space(
        CloudFoundrySpace.builder().id("spaceId").build()
      ).build());
    when(apps.getProcessState(any())).thenReturn(state);
    when(apps.createPackage(any()))
      .thenAnswer((Answer<String>) invocation -> {
        Object[] args = invocation.getArguments();
        return args[0].toString() + "_package";
      });
    return apps;
  }

  private DeployCloudFoundryServerGroupDescription getDeployCloudFoundryServerGroupDescription(boolean b) {
    final DeployCloudFoundryServerGroupDescription description = new DeployCloudFoundryServerGroupDescription()
      .setAccountName("account1")
      .setApplication("app1")
      .setStack("stack1")
      .setFreeFormDetails("detail1")
      .setArtifactCredentials(new ArtifactCredentialsFromString(
        "test",
        io.vavr.collection.List.of("a").asJava(),
        ""
      ))
      .setSpace(CloudFoundrySpace.builder().id("space1Id").name("space1").build())
      .setApplicationArtifact(Artifact.builder().reference("ref1").build())
      .setApplicationAttributes(new DeployCloudFoundryServerGroupDescription.ApplicationAttributes()
        .setInstances(7)
        .setMemory("1G")
        .setDiskQuota("2048M")
        .setHealthCheckType("http")
        .setHealthCheckHttpEndpoint("/health")
        .setBuildpacks(io.vavr.collection.List.of("buildpack1", "buildpack2").asJava())
        .setServices(io.vavr.collection.List.of("service1").asJava())
        .setEnv(HashMap.of(
          "token", "ASDF"
        ).toJavaMap()));
    description.setClient(cloudFoundryClient);
    description.setRegion("region1");
    description.setStartApplication(b);
    return description;
  }
}
