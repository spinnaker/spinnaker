/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.test;

import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactDownloader;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.EcsSpec;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import io.restassured.http.ContentType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.applicationautoscaling.ApplicationAutoScalingClient;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsResponse;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroup;

public class CreateServerGroupWithArtifactsSpec extends EcsSpec {

  @MockitoBean ArtifactDownloader mockArtifactDownloader;

  @MockitoBean ArtifactCredentialsRepository mockArtifactCredentialsRepository;

  private ArtifactCredentials mockArtifactCredentials = mock(ArtifactCredentials.class);

  private EcsClient mockEcsV2 = mock(EcsClient.class);

  private ElasticLoadBalancingV2Client mockELB = mock(ElasticLoadBalancingV2Client.class);

  private ApplicationAutoScalingClient mockAutoScalingV2 = mock(ApplicationAutoScalingClient.class);

  @BeforeEach
  public void setup() {

    // mock v2 ECS responses (used by EcsServerGroupNameResolver)
    when(mockEcsV2.listServices(any(ListServicesRequest.class)))
        .thenReturn(
            ListServicesResponse.builder().serviceArns(java.util.Collections.emptyList()).build());
    when(mockEcsV2.describeServices(any(DescribeServicesRequest.class)))
        .thenReturn(
            DescribeServicesResponse.builder().services(java.util.Collections.emptyList()).build());

    when(mockEcsV2.listAccountSettings(any(ListAccountSettingsRequest.class)))
        .thenReturn(ListAccountSettingsResponse.builder().build());

    when(mockEcsV2.registerTaskDefinition(any(RegisterTaskDefinitionRequest.class)))
        .thenAnswer(
            (Answer<RegisterTaskDefinitionResponse>)
                invocation -> {
                  RegisterTaskDefinitionRequest request = invocation.getArgument(0);
                  String testArn = "arn:aws:ecs:::task-definition/" + request.family() + ":1";
                  return RegisterTaskDefinitionResponse.builder()
                      .taskDefinition(TaskDefinition.builder().taskDefinitionArn(testArn).build())
                      .build();
                });

    when(mockEcsV2.createService(any(CreateServiceRequest.class)))
        .thenReturn(
            CreateServiceResponse.builder()
                .service(Service.builder().serviceName("createdService").build())
                .build());

    when(mockAwsProvider.getAmazonEcsV2(any(NetflixAmazonCredentials.class), anyString()))
        .thenReturn(mockEcsV2);

    when(mockArtifactCredentialsRepository.getCredentials(anyString(), anyString()))
        .thenReturn(mockArtifactCredentials);

    // mock v2 ELB responses
    when(mockELB.describeTargetGroups(any(DescribeTargetGroupsRequest.class)))
        .thenAnswer(
            (Answer<DescribeTargetGroupsResponse>)
                invocation -> {
                  DescribeTargetGroupsRequest request = invocation.getArgument(0);
                  String testArn =
                      "arn:aws:elasticloadbalancing:::targetgroup/"
                          + request.names().get(0)
                          + "/76tgredfc";
                  return DescribeTargetGroupsResponse.builder()
                      .targetGroups(TargetGroup.builder().targetGroupArn(testArn).build())
                      .build();
                });

    when(mockAwsProvider.getAmazonElasticLoadBalancingV2V2(
            any(NetflixAmazonCredentials.class), anyString()))
        .thenReturn(mockELB);

    // mock v2 Application Auto Scaling (source scalable target lookup for specs with a source)
    when(mockAutoScalingV2.describeScalableTargets(any(DescribeScalableTargetsRequest.class)))
        .thenReturn(
            DescribeScalableTargetsResponse.builder()
                .scalableTargets(java.util.Collections.emptyList())
                .build());
    when(mockAwsProvider.getAmazonApplicationAutoScalingV2(
            any(NetflixAmazonCredentials.class), anyString()))
        .thenReturn(mockAutoScalingV2);
  }

  @DisplayName(
      ".\n===\n"
          + "Given description w/ task def artifacts, EC2 launch type, and new target group fields, "
          + "successfully submit createServerGroup operation"
          + "\n===")
  @Test
  public void createServerGroup_ArtifactsEC2TgMappingsTest()
      throws IOException, InterruptedException {

    // given
    String url = getTestUrl(CREATE_SG_TEST_PATH);
    String requestBody =
        generateStringFromTestFile("/createServerGroup-artifact-EC2-targetGroupMappings.json");
    String expectedServerGroupName = "ecs-integArtifactsEC2TgMappingsStack-detailTest-v000";

    ByteArrayInputStream byteArrayInputStreamOfArtifactsForEC2Type =
        new ByteArrayInputStream(
            generateStringFromTestArtifactFile(
                    "/createServerGroup-artifact-EC2-targetGroup-artifactFile.json")
                .getBytes());

    when(mockArtifactDownloader.download(any(Artifact.class)))
        .thenReturn(byteArrayInputStreamOfArtifactsForEC2Type);

    String taskId =
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post(url)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("resourceUri", containsString("/task/"))
            .extract()
            .path("id");

    retryUntilTrue(
        () -> {
          List<Object> taskHistory =
              get(getTestUrl("/task/" + taskId))
                  .then()
                  .contentType(ContentType.JSON)
                  .extract()
                  .path("history");
          if (taskHistory
              .toString()
              .contains(String.format("Done creating 1 of %s", expectedServerGroupName))) {
            return true;
          }
          return false;
        },
        String.format("Failed to detect service creation in %s seconds", TASK_RETRY_SECONDS),
        TASK_RETRY_SECONDS);

    ArgumentCaptor<RegisterTaskDefinitionRequest> registerTaskDefArgs =
        ArgumentCaptor.forClass(RegisterTaskDefinitionRequest.class);
    verify(mockEcsV2).registerTaskDefinition(registerTaskDefArgs.capture());
    RegisterTaskDefinitionRequest seenTaskDefRequest = registerTaskDefArgs.getValue();
    assertEquals(expectedServerGroupName, seenTaskDefRequest.family() + "-v000");
    assertEquals(1, seenTaskDefRequest.containerDefinitions().size());
    assertEquals(
        "arn:aws:iam:::executionRole/testExecutionRole:1", seenTaskDefRequest.executionRoleArn());
    assertEquals("arn:aws:iam:::role/testTaskRole:1", seenTaskDefRequest.taskRoleArn());
    assertEquals("application", seenTaskDefRequest.containerDefinitions().get(0).name());
    assertEquals(
        "awslogs",
        seenTaskDefRequest.containerDefinitions().get(0).logConfiguration().logDriverAsString());
    assertEquals(
        "spinnaker-ecs-demo",
        seenTaskDefRequest
            .containerDefinitions()
            .get(0)
            .logConfiguration()
            .options()
            .get("awslogs-group"));

    ArgumentCaptor<DescribeTargetGroupsRequest> elbArgCaptor =
        ArgumentCaptor.forClass(DescribeTargetGroupsRequest.class);
    verify(mockELB).describeTargetGroups(elbArgCaptor.capture());

    ArgumentCaptor<CreateServiceRequest> createServiceArgs =
        ArgumentCaptor.forClass(CreateServiceRequest.class);
    verify(mockEcsV2).createService(createServiceArgs.capture());
    CreateServiceRequest seenCreateServRequest = createServiceArgs.getValue();
    assertEquals("EC2", seenCreateServRequest.launchTypeAsString());
    assertEquals(expectedServerGroupName, seenCreateServRequest.serviceName());
    assertEquals(1, seenCreateServRequest.loadBalancers().size());
    LoadBalancer serviceLB = seenCreateServRequest.loadBalancers().get(0);
    assertEquals("application", serviceLB.containerName());
    assertEquals(80, serviceLB.containerPort().intValue());
    assertEquals("integArtifactEC2TgMappings-cluster", seenCreateServRequest.cluster());
  }

  @DisplayName(
      ".\n===\n"
          + "Given description w/ task def artifacts, FARGATE launch type, and new target group fields, "
          + "successfully submit createServerGroup operation"
          + "\n===")
  @Test
  public void createServerGroup_ArtifactsFARGATETgMappingsTest()
      throws IOException, InterruptedException {

    // given
    String url = getTestUrl(CREATE_SG_TEST_PATH);
    String requestBody =
        generateStringFromTestFile("/createServerGroup-artifact-FARGATE-targetGroupMappings.json");
    String expectedServerGroupName = "ecs-integArtifactsFargateTgMappingsStack-detailTest-v000";

    ByteArrayInputStream byteArrayInputStreamOfArtifactsForFargateType =
        new ByteArrayInputStream(
            generateStringFromTestArtifactFile(
                    "/createServerGroup-artifact-Fargate-targetGroup-artifactFile.json")
                .getBytes());

    when(mockArtifactDownloader.download(any(Artifact.class)))
        .thenReturn(byteArrayInputStreamOfArtifactsForFargateType);

    String taskId =
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post(url)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("resourceUri", containsString("/task/"))
            .extract()
            .path("id");

    retryUntilTrue(
        () -> {
          List<Object> taskHistory =
              get(getTestUrl("/task/" + taskId))
                  .then()
                  .contentType(ContentType.JSON)
                  .extract()
                  .path("history");
          if (taskHistory
              .toString()
              .contains(String.format("Done creating 1 of %s", expectedServerGroupName))) {
            return true;
          }
          return false;
        },
        String.format("Failed to detect service creation in %s seconds", TASK_RETRY_SECONDS),
        TASK_RETRY_SECONDS);

    ArgumentCaptor<RegisterTaskDefinitionRequest> registerTaskDefArgs =
        ArgumentCaptor.forClass(RegisterTaskDefinitionRequest.class);
    verify(mockEcsV2).registerTaskDefinition(registerTaskDefArgs.capture());
    RegisterTaskDefinitionRequest seenTaskDefRequest = registerTaskDefArgs.getValue();
    assertEquals(expectedServerGroupName, seenTaskDefRequest.family() + "-v000");
    assertEquals(1, seenTaskDefRequest.containerDefinitions().size());
    assertEquals(
        "arn:aws:iam:::executionRole/testExecutionRole:1", seenTaskDefRequest.executionRoleArn());
    assertEquals("arn:aws:iam:::role/testTaskRole:1", seenTaskDefRequest.taskRoleArn());
    assertEquals("application", seenTaskDefRequest.containerDefinitions().get(0).name());
    assertEquals(
        "awslogs",
        seenTaskDefRequest.containerDefinitions().get(0).logConfiguration().logDriverAsString());
    assertEquals(
        "spinnaker-ecs-demo",
        seenTaskDefRequest
            .containerDefinitions()
            .get(0)
            .logConfiguration()
            .options()
            .get("awslogs-group"));

    ArgumentCaptor<DescribeTargetGroupsRequest> elbArgCaptor =
        ArgumentCaptor.forClass(DescribeTargetGroupsRequest.class);
    verify(mockELB).describeTargetGroups(elbArgCaptor.capture());

    ArgumentCaptor<CreateServiceRequest> createServiceArgs =
        ArgumentCaptor.forClass(CreateServiceRequest.class);
    verify(mockEcsV2).createService(createServiceArgs.capture());
    CreateServiceRequest seenCreateServRequest = createServiceArgs.getValue();
    assertEquals(0, seenCreateServRequest.capacityProviderStrategy().size());
    assertEquals("FARGATE", seenCreateServRequest.launchTypeAsString());
    assertEquals(expectedServerGroupName, seenCreateServRequest.serviceName());
    assertEquals(1, seenCreateServRequest.loadBalancers().size());
    LoadBalancer serviceLB = seenCreateServRequest.loadBalancers().get(0);
    assertEquals("application", serviceLB.containerName());
    assertEquals(80, serviceLB.containerPort().intValue());
    assertEquals("integArtifactsFargateTgMappings-cluster", seenCreateServRequest.cluster());
  }

  @DisplayName(
      ".\n===\n"
          + "Given description w/ task def artifacts and a FARGATE capacity provider strategy "
          + "successfully submits a createServerGroup operation"
          + "\n===")
  @Test
  public void createServerGroup_ArtifactsFARGATECapacityProviderTest()
      throws IOException, InterruptedException {

    // given
    String url = getTestUrl(CREATE_SG_TEST_PATH);
    String requestBody =
        generateStringFromTestFile(
            "/createServerGroup-artifact-FARGATE-capacityProviderStrategy.json");
    String expectedServerGroupName =
        "ecs-integArtifactsFargateCapacityProviderStrategyStack-detailTest-v000";

    ByteArrayInputStream byteArrayInputStreamOfArtifactsForFargateType =
        new ByteArrayInputStream(
            generateStringFromTestArtifactFile(
                    "/createServerGroup-artifact-Fargate-targetGroup-artifactFile.json")
                .getBytes());

    when(mockArtifactDownloader.download(any(Artifact.class)))
        .thenReturn(byteArrayInputStreamOfArtifactsForFargateType);

    String taskId =
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post(url)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("resourceUri", containsString("/task/"))
            .extract()
            .path("id");

    retryUntilTrue(
        () -> {
          List<Object> taskHistory =
              get(getTestUrl("/task/" + taskId))
                  .then()
                  .contentType(ContentType.JSON)
                  .extract()
                  .path("history");
          if (taskHistory
              .toString()
              .contains(String.format("Done creating 1 of %s", expectedServerGroupName))) {
            return true;
          }
          return false;
        },
        String.format("Failed to detect service creation in %s seconds", TASK_RETRY_SECONDS),
        TASK_RETRY_SECONDS);

    ArgumentCaptor<RegisterTaskDefinitionRequest> registerTaskDefArgs =
        ArgumentCaptor.forClass(RegisterTaskDefinitionRequest.class);
    verify(mockEcsV2).registerTaskDefinition(registerTaskDefArgs.capture());
    RegisterTaskDefinitionRequest seenTaskDefRequest = registerTaskDefArgs.getValue();
    assertEquals(expectedServerGroupName, seenTaskDefRequest.family() + "-v000");
    assertEquals(1, seenTaskDefRequest.containerDefinitions().size());
    assertEquals(
        "arn:aws:iam:::executionRole/testExecutionRole:1", seenTaskDefRequest.executionRoleArn());
    assertEquals("arn:aws:iam:::role/testTaskRole:1", seenTaskDefRequest.taskRoleArn());
    assertEquals("application", seenTaskDefRequest.containerDefinitions().get(0).name());
    assertEquals(
        "awslogs",
        seenTaskDefRequest.containerDefinitions().get(0).logConfiguration().logDriverAsString());
    assertEquals(
        "spinnaker-ecs-demo",
        seenTaskDefRequest
            .containerDefinitions()
            .get(0)
            .logConfiguration()
            .options()
            .get("awslogs-group"));

    ArgumentCaptor<DescribeTargetGroupsRequest> elbArgCaptor =
        ArgumentCaptor.forClass(DescribeTargetGroupsRequest.class);
    verify(mockELB).describeTargetGroups(elbArgCaptor.capture());

    ArgumentCaptor<CreateServiceRequest> createServiceArgs =
        ArgumentCaptor.forClass(CreateServiceRequest.class);
    verify(mockEcsV2).createService(createServiceArgs.capture());
    CreateServiceRequest seenCreateServRequest = createServiceArgs.getValue();
    assertEquals(1, seenCreateServRequest.capacityProviderStrategy().size());
    assertEquals(
        "FARGATE", seenCreateServRequest.capacityProviderStrategy().get(0).capacityProvider());
    assertNull(seenCreateServRequest.launchTypeAsString());
    assertEquals(expectedServerGroupName, seenCreateServRequest.serviceName());
    assertEquals(1, seenCreateServRequest.loadBalancers().size());
    LoadBalancer serviceLB = seenCreateServRequest.loadBalancers().get(0);
    assertEquals("application", serviceLB.containerName());
    assertEquals(80, serviceLB.containerPort().intValue());
    assertEquals(
        "integArtifactsFargateCapacityProviderStrategy-cluster", seenCreateServRequest.cluster());
  }

  @DisplayName(
      ".\n===\n"
          + "Given description w/ task def artifacts, EC2 launch type, and new target group fields "
          + "without container definition, gives an exception(Provided task definition does not contain any container definitions). "
          + "\n===")
  @Test
  public void createServerGroup_errorIfNoContainersTest() throws IOException, InterruptedException {

    // given
    String url = getTestUrl(CREATE_SG_TEST_PATH);
    String requestBody =
        generateStringFromTestFile("/createServerGroup-artifact-EC2-targetGroupMappings.json");

    ByteArrayInputStream byteArrayInputStreamOfArtifactsForEC2Type =
        new ByteArrayInputStream(
            generateStringFromTestArtifactFile(
                    "createServerGroup-artifact-EC2-targetGroup-WithNoContainers-artifactFile.json")
                .getBytes());

    when(mockArtifactDownloader.download(any(Artifact.class)))
        .thenReturn(byteArrayInputStreamOfArtifactsForEC2Type);

    String taskId =
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post(url)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("resourceUri", containsString("/task/"))
            .extract()
            .path("id");

    retryUntilTrue(
        () -> {
          HashMap<String, Object> status =
              get(getTestUrl("/task/" + taskId))
                  .then()
                  .contentType(ContentType.JSON)
                  .extract()
                  .path("status");

          return status.get("failed").equals(true);
        },
        String.format("Failed to detect task failure, in %s seconds", TASK_RETRY_SECONDS),
        TASK_RETRY_SECONDS);
  }

  @DisplayName(
      ".\n===\n"
          + "Given description w/ task def artifacts, EC2 launch type and "
          + "multiple load balancers successfully submit createServerGroup operation"
          + "\n===")
  @Test
  public void createServerGroup_ArtifactsEC2WithMultipleLBsTest()
      throws IOException, InterruptedException {

    // given
    String url = getTestUrl(CREATE_SG_TEST_PATH);
    String requestBody =
        generateStringFromTestFile(
            "/createServerGroup-artifact-EC2-TGMappings-multipleLBsAndContainers.json");
    String expectedServerGroupName =
        "ecs-integArtifactsEC2TgMappingsStackWithMultipleLBsAndContainers-detailTest-v000";

    ByteArrayInputStream byteArrayInputStreamOfArtifactsForEC2Type =
        new ByteArrayInputStream(
            generateStringFromTestArtifactFile(
                    "/createServerGroup-artifact-EC2-TGMappings-multipleLBsAndContainers-artifactFile.json")
                .getBytes());

    when(mockArtifactDownloader.download(any(Artifact.class)))
        .thenReturn(byteArrayInputStreamOfArtifactsForEC2Type);

    String taskId =
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post(url)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("resourceUri", containsString("/task/"))
            .extract()
            .path("id");

    retryUntilTrue(
        () -> {
          List<Object> taskHistory =
              get(getTestUrl("/task/" + taskId))
                  .then()
                  .contentType(ContentType.JSON)
                  .extract()
                  .path("history");
          if (taskHistory
              .toString()
              .contains(String.format("Done creating 1 of %s", expectedServerGroupName))) {
            return true;
          }
          return false;
        },
        String.format("Failed to detect service creation in %s seconds", TASK_RETRY_SECONDS),
        TASK_RETRY_SECONDS);

    ArgumentCaptor<RegisterTaskDefinitionRequest> registerTaskDefArgs =
        ArgumentCaptor.forClass(RegisterTaskDefinitionRequest.class);
    verify(mockEcsV2).registerTaskDefinition(registerTaskDefArgs.capture());
    RegisterTaskDefinitionRequest seenTaskDefRequest = registerTaskDefArgs.getValue();
    assertEquals(expectedServerGroupName, seenTaskDefRequest.family() + "-v000");
    assertEquals(
        "arn:aws:iam:::executionRole/testExecutionRole:1", seenTaskDefRequest.executionRoleArn());
    assertEquals("arn:aws:iam:::role/testTaskRole:1", seenTaskDefRequest.taskRoleArn());
    assertEquals(2, seenTaskDefRequest.containerDefinitions().size());
    ContainerDefinition container1 =
        seenTaskDefRequest.containerDefinitions().stream()
            .filter(container -> container.name().equals("application1"))
            .collect(Collectors.toList())
            .get(0);
    ContainerDefinition container2 =
        seenTaskDefRequest.containerDefinitions().stream()
            .filter(container -> container.name().equals("application2"))
            .collect(Collectors.toList())
            .get(0);
    assertEquals("application1", container1.name());
    assertEquals("app1/image", container1.image());
    assertEquals("application2", container2.name());
    assertEquals("app2/image", container2.image());
    assertEquals(80, container1.portMappings().get(0).containerPort());
    assertEquals(84, container2.portMappings().get(0).containerPort());
    assertEquals(
        "spinnaker-ecs-demo", container1.logConfiguration().options().get("awslogs-group"));
    assertEquals("awslogs", container1.logConfiguration().logDriverAsString());

    ArgumentCaptor<DescribeTargetGroupsRequest> elbArgCaptor =
        ArgumentCaptor.forClass(DescribeTargetGroupsRequest.class);
    verify(mockELB, times(2)).describeTargetGroups(elbArgCaptor.capture());

    ArgumentCaptor<CreateServiceRequest> createServiceArgs =
        ArgumentCaptor.forClass(CreateServiceRequest.class);
    verify(mockEcsV2).createService(createServiceArgs.capture());
    CreateServiceRequest seenCreateServRequest = createServiceArgs.getValue();
    assertEquals("EC2", seenCreateServRequest.launchTypeAsString());
    assertEquals(expectedServerGroupName, seenCreateServRequest.serviceName());
    assertEquals(2, seenCreateServRequest.loadBalancers().size());
    LoadBalancer serviceLB1 =
        seenCreateServRequest.loadBalancers().stream()
            .filter(lb -> lb.containerName().equals("application1"))
            .collect(Collectors.toList())
            .get(0);
    LoadBalancer serviceLB2 =
        seenCreateServRequest.loadBalancers().stream()
            .filter(lb -> lb.containerName().equals("application2"))
            .collect(Collectors.toList())
            .get(0);

    assertEquals("application1", serviceLB1.containerName());
    assertEquals(80, serviceLB1.containerPort().intValue());
    assertEquals(
        "arn:aws:elasticloadbalancing:::targetgroup/integArtifactEC2TgMappings-targetGroupForPort80/76tgredfc",
        serviceLB1.targetGroupArn());
    assertEquals("application2", serviceLB2.containerName());
    assertEquals(84, serviceLB2.containerPort().intValue());
    assertEquals(
        "arn:aws:elasticloadbalancing:::targetgroup/integArtifactEC2TgMappings-targetGroupForPort84/76tgredfc",
        serviceLB2.targetGroupArn());
    assertEquals(
        "integArtifactEC2TgMappingskWithMultipleLBsAndContainers-cluster",
        seenCreateServRequest.cluster());
  }

  @Test
  public void createServerGroup_ProcessedArtifactsEC2TgMappingsTest()
      throws IOException, InterruptedException {

    // given
    String url = getTestUrl(CREATE_SG_TEST_PATH);
    String requestBody =
        generateStringFromTestFile(
            "/createServerGroup-spelProcessedArtifact-EC2-targetGroupMappings.json");
    String expectedServerGroupName =
        "ecs-integSpELProcessedArtifactsEC2TgMappingsStack-detailTest-v000";

    String taskId =
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post(url)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("resourceUri", containsString("/task/"))
            .extract()
            .path("id");

    retryUntilTrue(
        () -> {
          List<Object> taskHistory =
              get(getTestUrl("/task/" + taskId))
                  .then()
                  .contentType(ContentType.JSON)
                  .extract()
                  .path("history");
          if (taskHistory
              .toString()
              .contains(String.format("Done creating 1 of %s", expectedServerGroupName))) {
            return true;
          }
          return false;
        },
        String.format("Failed to detect service creation in %s seconds", TASK_RETRY_SECONDS),
        TASK_RETRY_SECONDS);

    ArgumentCaptor<RegisterTaskDefinitionRequest> registerTaskDefArgs =
        ArgumentCaptor.forClass(RegisterTaskDefinitionRequest.class);
    verify(mockEcsV2).registerTaskDefinition(registerTaskDefArgs.capture());
    RegisterTaskDefinitionRequest seenTaskDefRequest = registerTaskDefArgs.getValue();
    assertEquals(expectedServerGroupName, seenTaskDefRequest.family() + "-v000");
    assertEquals(1, seenTaskDefRequest.containerDefinitions().size());
    assertEquals(
        "arn:aws:iam:::executionRole/testExecutionRole:1", seenTaskDefRequest.executionRoleArn());
    assertEquals("application", seenTaskDefRequest.containerDefinitions().get(0).name());
    assertEquals(
        "awslogs",
        seenTaskDefRequest.containerDefinitions().get(0).logConfiguration().logDriverAsString());
    assertEquals(
        "spinnaker-ecs-demo",
        seenTaskDefRequest
            .containerDefinitions()
            .get(0)
            .logConfiguration()
            .options()
            .get("awslogs-group"));

    ContainerDefinition containerDefinition =
        seenTaskDefRequest.containerDefinitions().stream()
            .filter(container -> container.name().equals("application"))
            .collect(Collectors.toList())
            .get(0);

    assertEquals(80, containerDefinition.portMappings().get(0).containerPort());

    assertEquals("tcp", containerDefinition.portMappings().get(0).protocolAsString());

    assertEquals(256, containerDefinition.cpu());

    assertEquals(512, containerDefinition.memoryReservation());

    assertEquals("PLACEHOLDER", containerDefinition.image());

    assertEquals("bridge", seenTaskDefRequest.networkModeAsString());
    assertEquals(
        "ecs-integSpELProcessedArtifactsEC2TgMappingsStack-detailTest",
        seenTaskDefRequest.family());
    assertEquals("bridge", seenTaskDefRequest.networkModeAsString());
  }
}
