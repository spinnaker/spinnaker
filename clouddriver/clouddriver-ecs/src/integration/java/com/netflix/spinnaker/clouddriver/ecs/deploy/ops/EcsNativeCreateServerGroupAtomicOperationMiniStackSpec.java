/*
 * Copyright 2026 Spinnaker contributors.
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

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.ecs.EcsNativeServiceTag;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.CreateServerGroupDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.EcsNativeCreateServerGroupDescription;
import com.netflix.spinnaker.clouddriver.ecs.services.SecurityGroupSelector;
import com.netflix.spinnaker.clouddriver.ecs.services.SubnetSelector;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.ministack.testcontainers.MiniStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.CreateSubnetRequest;
import software.amazon.awssdk.services.ec2.model.CreateVpcRequest;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.AssignPublicIp;
import software.amazon.awssdk.services.ecs.model.AwsVpcConfiguration;
import software.amazon.awssdk.services.ecs.model.Compatibility;
import software.amazon.awssdk.services.ecs.model.ContainerDefinition;
import software.amazon.awssdk.services.ecs.model.CreateClusterRequest;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.LaunchType;
import software.amazon.awssdk.services.ecs.model.NetworkConfiguration;
import software.amazon.awssdk.services.ecs.model.NetworkMode;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.Service;

/**
 * Exercises {@link EcsNativeCreateServerGroupAtomicOperation}'s in-place-update happy path (the
 * {@code ROLLING} deployment strategy) against a real MiniStack-backed {@link EcsClient}, instead
 * of the fully-mocked {@code ecs} client used by {@code
 * EcsNativeCreateServerGroupAtomicOperationSpec} (the Spock unit spec in {@code src/test}). That
 * confirms the production {@code registerTaskDefinition()}/{@code buildDeploymentConfiguration()}
 * code builds requests a real(-ish) ECS control plane actually accepts and completes a rollout for
 * -- not just that it builds the right-shaped request object.
 *
 * <p>Lives in the {@code integration} source set (run by the {@code integrationTest} Gradle task,
 * not the plain {@code test} task) since it pulls a Docker image and does real network I/O against
 * it -- slower and more network-dependent than the fast unit-test suite it would otherwise be
 * bundled into. Plain JUnit5 + Mockito rather than Spock/Groovy: unlike {@code src/test}, this
 * source set has no {@code groovy} directory configured (see {@code clouddriver-ecs.gradle}), and
 * every existing integration spec here is already plain Java.
 *
 * <p>The real ECS client is used for task-definition registration and service create/update calls.
 * The operation's credentials, task-role resolution, tagging-account check, autoscaling
 * registration, and subnet/security-group selectors are stubbed so the test stays focused on ECS
 * service behavior.
 *
 * <p>Pins released MiniStack {@code 1.5.22} for deterministic ECS rollout behavior and uses real
 * infrastructure for task execution and service-deployment state transitions.
 */
@Testcontainers
class EcsNativeCreateServerGroupAtomicOperationMiniStackSpec {

  private static final String REGION = "us-east-1";
  private static final Duration POLL_TIMEOUT = Duration.ofSeconds(120);
  private static final Duration POLL_INTERVAL = Duration.ofSeconds(3);

  @Container
  static final MiniStackContainer ministack =
      new MiniStackContainer("1.5.22").withRealInfrastructure();

  private static EcsClient ecs;
  private static String subnetId;
  private static final AtomicInteger SCENARIO_ID = new AtomicInteger();

  @BeforeAll
  static void setupOnce() {
    // operate() calls updateTaskStatus(), which reads this thread-local -- unset, that's an
    // immediate NPE regardless of which branch of operate() runs.
    TaskRepository.threadLocalTask.set(mock(Task.class));

    StaticCredentialsProvider credentials =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(ministack.getAccessKey(), ministack.getSecretKey()));
    URI endpoint = URI.create(ministack.getEndpoint());

    ecs =
        EcsClient.builder()
            .endpointOverride(endpoint)
            .credentialsProvider(credentials)
            .region(Region.of(REGION))
            .build();

    Ec2Client ec2 =
        Ec2Client.builder()
            .endpointOverride(endpoint)
            .credentialsProvider(credentials)
            .region(Region.of(REGION))
            .build();

    String vpcId =
        ec2.createVpc(CreateVpcRequest.builder().cidrBlock("10.0.0.0/16").build()).vpc().vpcId();
    subnetId =
        ec2.createSubnet(
                CreateSubnetRequest.builder().vpcId(vpcId).cidrBlock("10.0.1.0/24").build())
            .subnet()
            .subnetId();

    ec2.close();
  }

  private static String nextScenarioName(String label) {
    return "ecs-native-" + label + "-" + SCENARIO_ID.incrementAndGet();
  }

  private static void createCluster(String clusterName) {
    ecs.createCluster(CreateClusterRequest.builder().clusterName(clusterName).build());
  }

  private static String createRawService(
      String clusterName, String serviceName, boolean tagged, boolean enableExecuteCommand) {
    String taskDefinitionArn = registerRawTaskDefinition("bootstrap-" + serviceName, "sleep 600");
    ecs.createService(
        CreateServiceRequest.builder()
            .cluster(clusterName)
            .serviceName(serviceName)
            .taskDefinition(taskDefinitionArn)
            .desiredCount(1)
            .tags(
                tagged
                    ? Collections.singletonList(EcsNativeServiceTag.tag())
                    : Collections.emptyList())
            .enableExecuteCommand(enableExecuteCommand)
            .launchType(LaunchType.FARGATE)
            .networkConfiguration(
                NetworkConfiguration.builder()
                    .awsvpcConfiguration(
                        AwsVpcConfiguration.builder()
                            .subnets(subnetId)
                            .assignPublicIp(AssignPublicIp.ENABLED)
                            .build())
                    .build())
            .build());
    pollUntil(clusterName, serviceName, svc -> svc.runningCount() >= 1);
    return taskDefinitionArn;
  }

  private static EcsNativeCreateServerGroupDescription description(
      String clusterName, String application, String stack, String details) {
    EcsNativeCreateServerGroupDescription description = new EcsNativeCreateServerGroupDescription();
    description.setApplication(application);
    description.setStack(stack);
    description.setFreeFormDetails(details);
    description.setAccount("test");
    description.setEcsClusterName(clusterName);
    description.setDockerImageAddress("nginx:alpine");
    description.setLaunchType("FARGATE");
    description.setNetworkMode("awsvpc");
    description.setComputeUnits(256);
    description.setReservedMemory(512);
    description.setAvailabilityZones(
        Collections.singletonMap(REGION, Collections.singletonList(REGION + "a")));
    description.setCapacity(new ServerGroup.Capacity(1, 1, 1));
    description.setDeploymentStrategy("ROLLING");
    description.setMinimumHealthyPercent(100);
    description.setMaximumPercent(200);
    return description;
  }

  private static EcsNativeCreateServerGroupDescription sparseDescription(
      String clusterName, String application, String stack, String details) {
    EcsNativeCreateServerGroupDescription description = new EcsNativeCreateServerGroupDescription();
    description.setApplication(application);
    description.setStack(stack);
    description.setFreeFormDetails(details);
    description.setAccount("test");
    description.setEcsClusterName(clusterName);
    description.setDockerImageAddress("nginx:alpine");
    CreateServerGroupDescription.Source source = new CreateServerGroupDescription.Source();
    source.setRegion(REGION);
    description.setSource(source);
    return description;
  }

  private static EcsNativeCreateServerGroupAtomicOperation operation(
      EcsNativeCreateServerGroupDescription description) {
    EcsNativeCreateServerGroupAtomicOperation operation =
        spy(new EcsNativeCreateServerGroupAtomicOperation(description));
    AmazonCredentials credentials = mock(AmazonCredentials.class);
    doReturn("test").when(credentials).getName();
    doReturn(ecs).when(operation).getAmazonEcsClient();
    doReturn(credentials).when(operation).getCredentials();
    doReturn("arn:aws:iam::123456789012:role/ecsRole").when(operation).resolveTaskRoleArn(any());
    doReturn("arn:aws:iam::123456789012:role/ecsRole").when(operation).inferAssumedRoleArn(any());
    doReturn(true).when(operation).isTaggingEnabled(any());
    doReturn("service/" + description.getEcsClusterName() + "/" + description.getApplication())
        .when(operation)
        .registerAutoScalingGroup(any(), any(), any());

    SubnetSelector subnetSelector = mock(SubnetSelector.class);
    doReturn(Collections.singleton(subnetId))
        .when(subnetSelector)
        .resolveSubnetsIdsForMultipleSubnetTypes(any(), any(), any(), any());
    doReturn(Collections.emptySet()).when(subnetSelector).getSubnetVpcIds(any(), any(), any());
    operation.subnetSelector = subnetSelector;

    SecurityGroupSelector securityGroupSelector = mock(SecurityGroupSelector.class);
    doReturn(Collections.emptySet())
        .when(securityGroupSelector)
        .resolveSecurityGroupNames(any(), any(), any(), any());
    operation.securityGroupSelector = securityGroupSelector;
    return operation;
  }

  private static Service pollUntilCompleted(String clusterName, String serviceName) {
    return pollUntil(
        clusterName,
        serviceName,
        svc ->
            svc.deployments().stream()
                .anyMatch(
                    deployment ->
                        "PRIMARY".equals(deployment.status())
                            && "COMPLETED".equals(deployment.rolloutStateAsString())));
  }

  private static String registerRawTaskDefinition(String family, String sleepCommand) {
    return ecs.registerTaskDefinition(
            RegisterTaskDefinitionRequest.builder()
                .family(family)
                .networkMode(NetworkMode.AWSVPC)
                .requiresCompatibilities(Compatibility.FARGATE)
                .cpu("256")
                .memory("512")
                .containerDefinitions(
                    ContainerDefinition.builder()
                        .name("app")
                        .image("busybox:latest")
                        .command("sh", "-c", sleepCommand)
                        .build())
                .build())
        .taskDefinition()
        .taskDefinitionArn();
  }

  private static Service pollUntil(
      String clusterName, String serviceName, Predicate<Service> done) {
    Instant deadline = Instant.now().plus(POLL_TIMEOUT);
    Service last = null;
    while (Instant.now().isBefore(deadline)) {
      List<Service> services =
          ecs.describeServices(
                  DescribeServicesRequest.builder()
                      .cluster(clusterName)
                      .services(serviceName)
                      .build())
              .services();
      last = services.isEmpty() ? null : services.get(0);
      if (last != null && done.test(last)) {
        return last;
      }
      try {
        Thread.sleep(POLL_INTERVAL.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }
    throw new AssertionError(
        "timed out after " + POLL_TIMEOUT + " waiting for condition; last observed state: " + last);
  }

  @Test
  void initialNativeDeployCreatesTaggedDurableService() {
    String application = nextScenarioName("create");
    String clusterName = application + "-cluster";
    String serviceName = application + "-stack-service";
    createCluster(clusterName);

    EcsNativeCreateServerGroupDescription description =
        description(clusterName, application, "stack", "service");
    DeploymentResult result = operation(description).operate(Collections.emptyList());

    assertThat(result.getServerGroupNameByRegion()).containsEntry(REGION, serviceName);
    Service finalState = pollUntilCompleted(clusterName, serviceName);
    assertThat(finalState.status()).isEqualTo("ACTIVE");
    assertThat(EcsNativeServiceTag.isNative(finalState.tags())).isTrue();
    assertThat(finalState.desiredCount()).isEqualTo(1);
    assertThat(
            ecs.describeTaskDefinition(
                    DescribeTaskDefinitionRequest.builder()
                        .taskDefinition(finalState.taskDefinition())
                        .build())
                .taskDefinition()
                .family())
        .isEqualTo(serviceName);
  }

  @Test
  void untaggedFixedNameServiceFailsClosedBeforeRedeploy() {
    String application = nextScenarioName("untagged");
    String clusterName = application + "-cluster";
    String serviceName = application + "-stack-service";
    createCluster(clusterName);
    String originalTaskDefinition = createRawService(clusterName, serviceName, false, false);

    EcsNativeCreateServerGroupAtomicOperation operation =
        operation(sparseDescription(clusterName, application, "stack", "service"));

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> operation.operate(Collections.emptyList()));

    assertThat(exception).hasMessageContaining("not marked as owned by ecs-native");
    Service unchanged =
        ecs.describeServices(
                DescribeServicesRequest.builder()
                    .cluster(clusterName)
                    .services(serviceName)
                    .build())
            .services()
            .get(0);
    assertThat(unchanged.taskDefinition()).isEqualTo(originalTaskDefinition);
    assertThat(EcsNativeServiceTag.isNative(unchanged.tags())).isFalse();
  }

  @Test
  void sparseRedeployPreservesExistingServiceShape() {
    String application = nextScenarioName("sparse");
    String clusterName = application + "-cluster";
    String serviceName = application + "-stack-service";
    createCluster(clusterName);
    createRawService(clusterName, serviceName, true, true);
    Service before =
        ecs.describeServices(
                DescribeServicesRequest.builder()
                    .cluster(clusterName)
                    .services(serviceName)
                    .build())
            .services()
            .get(0);

    DeploymentResult result =
        operation(sparseDescription(clusterName, application, "stack", "service"))
            .operate(Collections.emptyList());
    assertThat(result.getServerGroupNameByRegion()).containsEntry(REGION, serviceName);

    Service after = pollUntilCompleted(clusterName, serviceName);
    assertThat(after.serviceName()).isEqualTo(before.serviceName());
    assertThat(after.desiredCount()).isEqualTo(before.desiredCount());
    assertThat(after.networkConfiguration()).isEqualTo(before.networkConfiguration());
    assertThat(after.enableExecuteCommand()).isEqualTo(before.enableExecuteCommand());
    assertThat(after.taskDefinition()).isNotEqualTo(before.taskDefinition());
  }

  @Test
  void explicitEcsExecDisablePersistsOnRedeploy() {
    String application = nextScenarioName("exec-disable");
    String clusterName = application + "-cluster";
    String serviceName = application + "-stack-service";
    createCluster(clusterName);
    createRawService(clusterName, serviceName, true, true);

    EcsNativeCreateServerGroupDescription description =
        sparseDescription(clusterName, application, "stack", "service");
    description.setEnableExecuteCommand(false);
    operation(description).operate(Collections.emptyList());

    Service after = pollUntilCompleted(clusterName, serviceName);
    assertThat(after.enableExecuteCommand()).isFalse();
    assertThat(after.serviceName()).isEqualTo(serviceName);
  }

  @Test
  void repeatedRollingRedeploysKeepOneDurableService() {
    String application = nextScenarioName("repeat");
    String clusterName = application + "-cluster";
    String serviceName = application + "-stack-service";
    createCluster(clusterName);
    createRawService(clusterName, serviceName, true, false);

    List<String> taskDefinitions = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      EcsNativeCreateServerGroupDescription description =
          description(clusterName, application, "stack", "service");
      operation(description).operate(Collections.emptyList());
      Service after = pollUntilCompleted(clusterName, serviceName);
      taskDefinitions.add(after.taskDefinition());
      assertThat(after.serviceName()).isEqualTo(serviceName);
      assertThat(after.desiredCount()).isEqualTo(1);
      assertThat(after.runningCount()).isEqualTo(1);
    }

    assertThat(taskDefinitions).doesNotHaveDuplicates();
    Service finalState =
        ecs.describeServices(
                DescribeServicesRequest.builder()
                    .cluster(clusterName)
                    .services(serviceName)
                    .build())
            .services()
            .get(0);
    assertThat(
            finalState.deployments().stream()
                .anyMatch(
                    deployment ->
                        "PRIMARY".equals(deployment.status())
                            && "COMPLETED".equals(deployment.rolloutStateAsString())))
        .isTrue();
  }

  @Test
  void rollingEcsNativeInPlaceUpdateAgainstARealEcsCompatibleBackendActuallyCompletes() {
    String application = nextScenarioName("rolling");
    String clusterName = application + "-cluster";
    String serviceName = application + "-stack-service";
    createCluster(clusterName);
    createRawService(clusterName, serviceName, true, false);

    EcsNativeCreateServerGroupDescription description =
        description(clusterName, application, "stack", "service");
    DeploymentResult result = operation(description).operate(Collections.emptyList());

    assertThat(result.getServerGroupNameByRegion()).containsEntry(REGION, serviceName);
    Service finalState = pollUntilCompleted(clusterName, serviceName);
    assertThat(finalState.deployments().get(0).desiredCount()).isEqualTo(1);
    assertThat(finalState.deployments().get(0).runningCount()).isEqualTo(1);
  }
}
