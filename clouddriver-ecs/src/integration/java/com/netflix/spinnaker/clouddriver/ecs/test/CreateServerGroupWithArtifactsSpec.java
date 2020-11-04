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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.amazonaws.services.applicationautoscaling.AWSApplicationAutoScalingClient;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsResult;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.*;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsResult;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.springframework.boot.test.mock.mockito.MockBean;

public class CreateServerGroupWithArtifactsSpec extends EcsSpec {

  @MockBean ArtifactDownloader mockArtifactDownloader;

  @MockBean ArtifactCredentialsRepository mockArtifactCredentialsRepository;

  private ArtifactCredentials mockArtifactCredentials = mock(ArtifactCredentials.class);

  private AWSApplicationAutoScalingClient mockAWSApplicationAutoScalingClient =
      mock(AWSApplicationAutoScalingClient.class);

  private AmazonECS mockECS = mock(AmazonECS.class);

  private AmazonElasticLoadBalancing mockELB = mock(AmazonElasticLoadBalancing.class);

  @BeforeEach
  public void setup() {

    // mocking calls
    when(mockECS.listServices(any(ListServicesRequest.class))).thenReturn(new ListServicesResult());

    when(mockECS.describeServices(any(DescribeServicesRequest.class)))
        .thenReturn(new DescribeServicesResult());

    when(mockECS.createService(any(CreateServiceRequest.class)))
        .thenReturn(
            new CreateServiceResult().withService(new Service().withServiceName("createdService")));

    when(mockECS.registerTaskDefinition(any(RegisterTaskDefinitionRequest.class)))
        .thenAnswer(
            (Answer<RegisterTaskDefinitionResult>)
                invocation -> {
                  RegisterTaskDefinitionRequest request =
                      (RegisterTaskDefinitionRequest) invocation.getArguments()[0];
                  String testArn = "arn:aws:ecs:::task-definition/" + request.getFamily() + ":1";
                  TaskDefinition taskDef = new TaskDefinition().withTaskDefinitionArn(testArn);
                  return new RegisterTaskDefinitionResult().withTaskDefinition(taskDef);
                });

    when(mockArtifactCredentialsRepository.getCredentials(anyString(), anyString()))
        .thenReturn(mockArtifactCredentials);

    when(mockAWSApplicationAutoScalingClient.describeScalableTargets(
            any(DescribeScalableTargetsRequest.class)))
        .thenReturn(new DescribeScalableTargetsResult());

    // mock ELB responses
    when(mockELB.describeTargetGroups(any(DescribeTargetGroupsRequest.class)))
        .thenAnswer(
            (Answer<DescribeTargetGroupsResult>)
                invocation -> {
                  DescribeTargetGroupsRequest request =
                      (DescribeTargetGroupsRequest) invocation.getArguments()[0];
                  String testArn =
                      "arn:aws:elasticloadbalancing:::targetgroup/"
                          + request.getNames().get(0)
                          + "/76tgredfc";
                  TargetGroup testTg = new TargetGroup().withTargetGroupArn(testArn);

                  return new DescribeTargetGroupsResult().withTargetGroups(testTg);
                });

    when(mockAwsProvider.getAmazonEcs(
            any(NetflixAmazonCredentials.class), anyString(), anyBoolean()))
        .thenReturn(mockECS);

    when(mockAwsProvider.getAmazonApplicationAutoScaling(
            any(NetflixAmazonCredentials.class), anyString(), anyBoolean()))
        .thenReturn(mockAWSApplicationAutoScalingClient);

    when(mockAwsProvider.getAmazonElasticLoadBalancingV2(
            any(NetflixAmazonCredentials.class), anyString(), anyBoolean()))
        .thenReturn(mockELB);
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

    setEcsAccountCreds();

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
    verify(mockECS).registerTaskDefinition(registerTaskDefArgs.capture());
    RegisterTaskDefinitionRequest seenTaskDefRequest = registerTaskDefArgs.getValue();
    assertEquals(expectedServerGroupName, seenTaskDefRequest.getFamily() + "-v000");
    assertEquals(1, seenTaskDefRequest.getContainerDefinitions().size());
    assertEquals(
        "arn:aws:iam:::executionRole/testExecutionRole:1",
        seenTaskDefRequest.getExecutionRoleArn());
    assertEquals("arn:aws:iam:::role/testTaskRole:1", seenTaskDefRequest.getTaskRoleArn());
    assertEquals("application", seenTaskDefRequest.getContainerDefinitions().get(0).getName());
    assertEquals(
        "awslogs",
        seenTaskDefRequest.getContainerDefinitions().get(0).getLogConfiguration().getLogDriver());
    assertEquals(
        "spinnaker-ecs-demo",
        seenTaskDefRequest
            .getContainerDefinitions()
            .get(0)
            .getLogConfiguration()
            .getOptions()
            .get("awslogs-group"));

    ArgumentCaptor<DescribeTargetGroupsRequest> elbArgCaptor =
        ArgumentCaptor.forClass(DescribeTargetGroupsRequest.class);
    verify(mockELB).describeTargetGroups(elbArgCaptor.capture());

    ArgumentCaptor<CreateServiceRequest> createServiceArgs =
        ArgumentCaptor.forClass(CreateServiceRequest.class);
    verify(mockECS).createService(createServiceArgs.capture());
    CreateServiceRequest seenCreateServRequest = createServiceArgs.getValue();
    assertEquals("EC2", seenCreateServRequest.getLaunchType());
    assertEquals(expectedServerGroupName, seenCreateServRequest.getServiceName());
    assertEquals(1, seenCreateServRequest.getLoadBalancers().size());
    LoadBalancer serviceLB = seenCreateServRequest.getLoadBalancers().get(0);
    assertEquals("application", serviceLB.getContainerName());
    assertEquals(80, serviceLB.getContainerPort().intValue());
    assertEquals("integArtifactEC2TgMappings-cluster", seenCreateServRequest.getCluster());
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

    setEcsAccountCreds();

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
    verify(mockECS).registerTaskDefinition(registerTaskDefArgs.capture());
    RegisterTaskDefinitionRequest seenTaskDefRequest = registerTaskDefArgs.getValue();
    assertEquals(expectedServerGroupName, seenTaskDefRequest.getFamily() + "-v000");
    assertEquals(1, seenTaskDefRequest.getContainerDefinitions().size());
    assertEquals(
        "arn:aws:iam:::executionRole/testExecutionRole:1",
        seenTaskDefRequest.getExecutionRoleArn());
    assertEquals("arn:aws:iam:::role/testTaskRole:1", seenTaskDefRequest.getTaskRoleArn());
    assertEquals("application", seenTaskDefRequest.getContainerDefinitions().get(0).getName());
    assertEquals(
        "awslogs",
        seenTaskDefRequest.getContainerDefinitions().get(0).getLogConfiguration().getLogDriver());
    assertEquals(
        "spinnaker-ecs-demo",
        seenTaskDefRequest
            .getContainerDefinitions()
            .get(0)
            .getLogConfiguration()
            .getOptions()
            .get("awslogs-group"));

    ArgumentCaptor<DescribeTargetGroupsRequest> elbArgCaptor =
        ArgumentCaptor.forClass(DescribeTargetGroupsRequest.class);
    verify(mockELB).describeTargetGroups(elbArgCaptor.capture());

    ArgumentCaptor<CreateServiceRequest> createServiceArgs =
        ArgumentCaptor.forClass(CreateServiceRequest.class);
    verify(mockECS).createService(createServiceArgs.capture());
    CreateServiceRequest seenCreateServRequest = createServiceArgs.getValue();
    assertEquals("FARGATE", seenCreateServRequest.getLaunchType());
    assertEquals(expectedServerGroupName, seenCreateServRequest.getServiceName());
    assertEquals(1, seenCreateServRequest.getLoadBalancers().size());
    LoadBalancer serviceLB = seenCreateServRequest.getLoadBalancers().get(0);
    assertEquals("application", serviceLB.getContainerName());
    assertEquals(80, serviceLB.getContainerPort().intValue());
    assertEquals("integArtifactsFargateTgMappings-cluster", seenCreateServRequest.getCluster());
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

    setEcsAccountCreds();

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
}
