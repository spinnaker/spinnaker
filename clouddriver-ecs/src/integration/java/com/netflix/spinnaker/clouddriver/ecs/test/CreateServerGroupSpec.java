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
import static org.mockito.Mockito.*;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.*;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsResult;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.EcsSpec;
import io.restassured.http.ContentType;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

public class CreateServerGroupSpec extends EcsSpec {

  private AmazonECS mockECS = mock(AmazonECS.class);
  private AmazonElasticLoadBalancing mockELB = mock(AmazonElasticLoadBalancing.class);

  @BeforeEach
  public void setup() {
    // mock ECS responses
    when(mockECS.listServices(any(ListServicesRequest.class))).thenReturn(new ListServicesResult());
    when(mockECS.describeServices(any(DescribeServicesRequest.class)))
        .thenReturn(new DescribeServicesResult());
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
    when(mockECS.createService(any(CreateServiceRequest.class)))
        .thenReturn(
            new CreateServiceResult().withService(new Service().withServiceName("createdService")));

    when(mockAwsProvider.getAmazonEcs(
            any(NetflixAmazonCredentials.class), anyString(), anyBoolean()))
        .thenReturn(mockECS);

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

    when(mockAwsProvider.getAmazonElasticLoadBalancingV2(
            any(NetflixAmazonCredentials.class), anyString(), anyBoolean()))
        .thenReturn(mockELB);
  }

  @DisplayName(
      ".\n===\n"
          + "Given description w/ inputs, EC2 launch type, and legacy target group fields, "
          + "successfully submit createServerGroup operation"
          + "\n===")
  @Test
  public void createServerGroup_InputsEc2LegacyTargetGroupTest()
      throws IOException, InterruptedException {

    // given
    String url = getTestUrl(CREATE_SG_TEST_PATH);
    String requestBody = generateStringFromTestFile("/createServerGroup-inputs-ec2.json");
    String expectedServerGroupName = "ecs-integInputsEc2LegacyTargetGroup";
    setEcsAccountCreds();

    // when
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
              .contains(String.format("Done creating 1 of %s-v000", expectedServerGroupName))) {
            return true;
          }
          return false;
        },
        String.format("Failed to detect service creation in %s seconds", TASK_RETRY_SECONDS),
        TASK_RETRY_SECONDS);

    // then
    ArgumentCaptor<RegisterTaskDefinitionRequest> registerTaskDefArgs =
        ArgumentCaptor.forClass(RegisterTaskDefinitionRequest.class);
    verify(mockECS).registerTaskDefinition(registerTaskDefArgs.capture());
    RegisterTaskDefinitionRequest seenTaskDefRequest = registerTaskDefArgs.getValue();
    assertEquals(expectedServerGroupName, seenTaskDefRequest.getFamily());
    assertEquals(1, seenTaskDefRequest.getContainerDefinitions().size());

    ArgumentCaptor<DescribeTargetGroupsRequest> elbArgCaptor =
        ArgumentCaptor.forClass(DescribeTargetGroupsRequest.class);
    verify(mockELB).describeTargetGroups(elbArgCaptor.capture());

    ArgumentCaptor<CreateServiceRequest> createServiceArgs =
        ArgumentCaptor.forClass(CreateServiceRequest.class);
    verify(mockECS).createService(createServiceArgs.capture());
    CreateServiceRequest seenCreateServRequest = createServiceArgs.getValue();
    assertEquals("EC2", seenCreateServRequest.getLaunchType());
    assertEquals(expectedServerGroupName + "-v000", seenCreateServRequest.getServiceName());
    assertEquals(1, seenCreateServRequest.getLoadBalancers().size());
    LoadBalancer serviceLB = seenCreateServRequest.getLoadBalancers().get(0);
    assertEquals("v000", serviceLB.getContainerName());
    assertEquals(80, serviceLB.getContainerPort().intValue());
    assertEquals("integInputsEc2LegacyTargetGroup-cluster", seenCreateServRequest.getCluster());
  }

  @DisplayName(
      ".\n===\n"
          + "Given description w/ task def inputs, FARGATE launch type, and legacy target group fields, "
          + "successfully submit createServerGroup operation"
          + "\n===")
  @Test
  public void createServerGroup_InputsFargateLegacyTargetGroupTest()
      throws IOException, InterruptedException {

    // given
    String url = getTestUrl(CREATE_SG_TEST_PATH);
    String requestBody =
        generateStringFromTestFile(
            "/createServerGroupOperation-inputs-fargate-legacyTargetGroup.json");
    String expectedServerGroupName = "ecs-integInputsFargateLegacyTargetGroup";
    setEcsAccountCreds();

    // when
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
              .contains(String.format("Done creating 1 of %s-v000", expectedServerGroupName))) {
            return true;
          }
          return false;
        },
        String.format("Failed to detect service creation in %s seconds", TASK_RETRY_SECONDS),
        TASK_RETRY_SECONDS);

    // then
    ArgumentCaptor<RegisterTaskDefinitionRequest> registerTaskDefArgs =
        ArgumentCaptor.forClass(RegisterTaskDefinitionRequest.class);
    verify(mockECS).registerTaskDefinition(registerTaskDefArgs.capture());
    RegisterTaskDefinitionRequest seenTaskDefRequest = registerTaskDefArgs.getValue();
    assertEquals(expectedServerGroupName, seenTaskDefRequest.getFamily());
    assertEquals(1, seenTaskDefRequest.getContainerDefinitions().size());
    assertEquals("aws-vpc", seenTaskDefRequest.getNetworkMode());

    ArgumentCaptor<DescribeTargetGroupsRequest> elbArgCaptor =
        ArgumentCaptor.forClass(DescribeTargetGroupsRequest.class);
    verify(mockELB).describeTargetGroups(elbArgCaptor.capture());

    ArgumentCaptor<CreateServiceRequest> createServiceArgs =
        ArgumentCaptor.forClass(CreateServiceRequest.class);
    verify(mockECS).createService(createServiceArgs.capture());
    CreateServiceRequest seenCreateServRequest = createServiceArgs.getValue();
    assertEquals("FARGATE", seenCreateServRequest.getLaunchType());
    assertEquals(expectedServerGroupName + "-v000", seenCreateServRequest.getServiceName());
    assertEquals(1, seenCreateServRequest.getLoadBalancers().size());
    LoadBalancer serviceLB = seenCreateServRequest.getLoadBalancers().get(0);
    assertEquals("v000", serviceLB.getContainerName());
    assertEquals(80, serviceLB.getContainerPort().intValue());
    assertEquals("integInputsFargateLegacyTargetGroup-cluster", seenCreateServRequest.getCluster());
  }

  @DisplayName(
      ".\n===\n"
          + "Given description w/ task def inputs, FARGATE launch type, and new target group fields, "
          + "successfully submit createServerGroup operation"
          + "\n===")
  @Test
  public void createServerGroup_InputsFargateTgMappingsTest()
      throws IOException, InterruptedException {

    // given
    String url = getTestUrl(CREATE_SG_TEST_PATH);
    String requestBody =
        generateStringFromTestFile(
            "/createServerGroupOperation-inputs-fargate-targetGroupMappings.json");
    String expectedServerGroupName = "ecs-integInputsFargateTgMappings";
    setEcsAccountCreds();

    // when
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
              .contains(String.format("Done creating 1 of %s-v000", expectedServerGroupName))) {
            return true;
          }
          return false;
        },
        String.format("Failed to detect service creation in %s seconds", TASK_RETRY_SECONDS),
        TASK_RETRY_SECONDS);

    // then
    ArgumentCaptor<RegisterTaskDefinitionRequest> registerTaskDefArgs =
        ArgumentCaptor.forClass(RegisterTaskDefinitionRequest.class);
    verify(mockECS).registerTaskDefinition(registerTaskDefArgs.capture());
    RegisterTaskDefinitionRequest seenTaskDefRequest = registerTaskDefArgs.getValue();
    assertEquals(expectedServerGroupName, seenTaskDefRequest.getFamily());
    assertEquals(1, seenTaskDefRequest.getContainerDefinitions().size());
    assertEquals("aws-vpc", seenTaskDefRequest.getNetworkMode());

    ArgumentCaptor<DescribeTargetGroupsRequest> elbArgCaptor =
        ArgumentCaptor.forClass(DescribeTargetGroupsRequest.class);
    verify(mockELB).describeTargetGroups(elbArgCaptor.capture());

    ArgumentCaptor<CreateServiceRequest> createServiceArgs =
        ArgumentCaptor.forClass(CreateServiceRequest.class);
    verify(mockECS).createService(createServiceArgs.capture());
    CreateServiceRequest seenCreateServRequest = createServiceArgs.getValue();
    assertEquals(expectedServerGroupName + "-v000", seenCreateServRequest.getServiceName());
    assertEquals(1, seenCreateServRequest.getLoadBalancers().size());
    assertEquals("FARGATE", seenCreateServRequest.getLaunchType());
    // assert network stuff is set
    LoadBalancer serviceLB = seenCreateServRequest.getLoadBalancers().get(0);
    assertEquals("main", serviceLB.getContainerName());
    assertEquals(80, serviceLB.getContainerPort().intValue());
    assertEquals("integInputsFargateTgMappings-cluster", seenCreateServRequest.getCluster());
  }

  @DisplayName(
      ".\n===\n"
          + "Given description w/ task def inputs,"
          + "task should fail if ECS service creation fails"
          + "\n===")
  @Test
  public void createServerGroup_errorIfCreateServiceFails()
      throws IOException, InterruptedException {
    // given
    String url = getTestUrl(CREATE_SG_TEST_PATH);
    String requestBody =
        generateStringFromTestFile("/createServerGroup-inputs-ecsCreateFails.json");
    setEcsAccountCreds();

    // when
    Mockito.doThrow(new InvalidParameterException("Something is wrong."))
        .when(mockECS)
        .createService(any(CreateServiceRequest.class));

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

    // then
    retryUntilTrue(
        () -> {
          HashMap<String, Boolean> status =
              get(getTestUrl("/task/" + taskId))
                  .then()
                  .contentType(ContentType.JSON)
                  .extract()
                  .path("status");

          return status.get("failed").equals(true);
        },
        String.format("Failed to observe task failure after %s seconds", TASK_RETRY_SECONDS),
        TASK_RETRY_SECONDS);
  }

  @DisplayName(
      ".\n===\n"
          + "Given description w/ inputs, EC2 launch type "
          + "with no load balancing successfully submit createServerGroup operation"
          + "\n===")
  @Test
  public void createServerGroup_InputsEc2WithoutLoadBalacingTest()
      throws IOException, InterruptedException {

    // given
    String url = getTestUrl(CREATE_SG_TEST_PATH);
    String requestBody =
        generateStringFromTestFile("/createServerGroup-inputs-ec2-withoutLoadBalacing.json");
    String expectedServerGroupName = "ecs-integInputsEc2NoLoadBalancing";
    setEcsAccountCreds();

    // when
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
              .contains(String.format("Done creating 1 of %s-v000", expectedServerGroupName))) {
            return true;
          }
          return false;
        },
        String.format("Failed to detect service creation in %s seconds", TASK_RETRY_SECONDS),
        TASK_RETRY_SECONDS);

    // then
    ArgumentCaptor<RegisterTaskDefinitionRequest> registerTaskDefArgs =
        ArgumentCaptor.forClass(RegisterTaskDefinitionRequest.class);
    verify(mockECS).registerTaskDefinition(registerTaskDefArgs.capture());
    RegisterTaskDefinitionRequest seenTaskDefRequest = registerTaskDefArgs.getValue();
    assertEquals(expectedServerGroupName, seenTaskDefRequest.getFamily());
    assertEquals(1, seenTaskDefRequest.getContainerDefinitions().size());

    ArgumentCaptor<CreateServiceRequest> createServiceArgs =
        ArgumentCaptor.forClass(CreateServiceRequest.class);
    verify(mockECS).createService(createServiceArgs.capture());
    CreateServiceRequest seenCreateServRequest = createServiceArgs.getValue();
    assertEquals("EC2", seenCreateServRequest.getLaunchType());
    assertEquals(expectedServerGroupName + "-v000", seenCreateServRequest.getServiceName());
  }

  @DisplayName(
      ".\n===\n"
          + "Given description w/ inputs, EC2 launch type"
          + "and service discovery registry fields, "
          + "successfully submit createServerGroup operation"
          + "\n===")
  @Test
  public void createServerGroup_InputsEc2ServiceDiscoveryTest()
      throws IOException, InterruptedException {

    // given
    String url = getTestUrl(CREATE_SG_TEST_PATH);
    String requestBody =
        generateStringFromTestFile("/createServerGroup-inputs-ec2-serviceDiscovery.json");
    String expectedServerGroupName = "ecs-integInputsEc2WithServiceDiscovery";
    setEcsAccountCreds();

    // when
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
              .contains(String.format("Done creating 1 of %s-v000", expectedServerGroupName))) {
            return true;
          }
          return false;
        },
        String.format("Failed to detect service creation in %s seconds", TASK_RETRY_SECONDS),
        TASK_RETRY_SECONDS);

    // then
    ArgumentCaptor<RegisterTaskDefinitionRequest> registerTaskDefArgs =
        ArgumentCaptor.forClass(RegisterTaskDefinitionRequest.class);
    verify(mockECS).registerTaskDefinition(registerTaskDefArgs.capture());
    RegisterTaskDefinitionRequest seenTaskDefRequest = registerTaskDefArgs.getValue();
    assertEquals(expectedServerGroupName, seenTaskDefRequest.getFamily());
    assertEquals(1, seenTaskDefRequest.getContainerDefinitions().size());

    ArgumentCaptor<CreateServiceRequest> createServiceArgs =
        ArgumentCaptor.forClass(CreateServiceRequest.class);
    verify(mockECS).createService(createServiceArgs.capture());
    CreateServiceRequest seenCreateServRequest = createServiceArgs.getValue();
    assertEquals("EC2", seenCreateServRequest.getLaunchType());
    assertEquals(expectedServerGroupName + "-v000", seenCreateServRequest.getServiceName());
    assertEquals(80, seenCreateServRequest.getServiceRegistries().get(0).getContainerPort());
    assertEquals(
        "arn:aws:servicediscovery:us-west-2:910995322324:service/srv-ckeydmrhzmqh6yfz",
        seenCreateServRequest.getServiceRegistries().get(0).getRegistryArn());
    assertEquals(
        true,
        seenCreateServRequest.getServiceRegistries().get(0).getContainerName().contains("v000"));
  }
}
