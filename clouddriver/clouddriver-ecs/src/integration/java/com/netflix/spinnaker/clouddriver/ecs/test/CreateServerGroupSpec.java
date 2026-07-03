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
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroup;

public class CreateServerGroupSpec extends EcsSpec {

  private EcsClient mockEcsV2 = mock(EcsClient.class);
  private ElasticLoadBalancingV2Client mockELB = mock(ElasticLoadBalancingV2Client.class);

  @BeforeEach
  public void setup() {
    // mock v2 ECS responses used by EcsServerGroupNameResolver
    when(mockEcsV2.listServices(any(ListServicesRequest.class)))
        .thenReturn(
            ListServicesResponse.builder().serviceArns(java.util.Collections.emptyList()).build());
    when(mockEcsV2.describeServices(any(DescribeServicesRequest.class)))
        .thenReturn(
            DescribeServicesResponse.builder().services(java.util.Collections.emptyList()).build());

    // mock v2 ECS responses
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
    verify(mockEcsV2).registerTaskDefinition(registerTaskDefArgs.capture());
    RegisterTaskDefinitionRequest seenTaskDefRequest = registerTaskDefArgs.getValue();
    assertEquals(expectedServerGroupName, seenTaskDefRequest.family());
    assertEquals(1, seenTaskDefRequest.containerDefinitions().size());

    ArgumentCaptor<DescribeTargetGroupsRequest> elbArgCaptor =
        ArgumentCaptor.forClass(DescribeTargetGroupsRequest.class);
    verify(mockELB).describeTargetGroups(elbArgCaptor.capture());

    ArgumentCaptor<CreateServiceRequest> createServiceArgs =
        ArgumentCaptor.forClass(CreateServiceRequest.class);
    verify(mockEcsV2).createService(createServiceArgs.capture());
    CreateServiceRequest seenCreateServRequest = createServiceArgs.getValue();
    assertEquals("EC2", seenCreateServRequest.launchTypeAsString());
    assertEquals(expectedServerGroupName + "-v000", seenCreateServRequest.serviceName());
    assertEquals(1, seenCreateServRequest.loadBalancers().size());
    LoadBalancer serviceLB = seenCreateServRequest.loadBalancers().get(0);
    assertEquals("v000", serviceLB.containerName());
    assertEquals(80, serviceLB.containerPort().intValue());
    assertEquals("integInputsEc2LegacyTargetGroup-cluster", seenCreateServRequest.cluster());
    assertEquals(0, seenCreateServRequest.tags().size());
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
    verify(mockEcsV2).registerTaskDefinition(registerTaskDefArgs.capture());
    RegisterTaskDefinitionRequest seenTaskDefRequest = registerTaskDefArgs.getValue();
    assertEquals(expectedServerGroupName, seenTaskDefRequest.family());
    assertEquals(1, seenTaskDefRequest.containerDefinitions().size());
    assertEquals("aws-vpc", seenTaskDefRequest.networkModeAsString());

    ArgumentCaptor<DescribeTargetGroupsRequest> elbArgCaptor =
        ArgumentCaptor.forClass(DescribeTargetGroupsRequest.class);
    verify(mockELB).describeTargetGroups(elbArgCaptor.capture());

    ArgumentCaptor<CreateServiceRequest> createServiceArgs =
        ArgumentCaptor.forClass(CreateServiceRequest.class);
    verify(mockEcsV2).createService(createServiceArgs.capture());
    CreateServiceRequest seenCreateServRequest = createServiceArgs.getValue();
    assertEquals("FARGATE", seenCreateServRequest.launchTypeAsString());
    assertEquals(expectedServerGroupName + "-v000", seenCreateServRequest.serviceName());
    assertEquals(1, seenCreateServRequest.loadBalancers().size());
    LoadBalancer serviceLB = seenCreateServRequest.loadBalancers().get(0);
    assertEquals("v000", serviceLB.containerName());
    assertEquals(80, serviceLB.containerPort().intValue());
    assertEquals("integInputsFargateLegacyTargetGroup-cluster", seenCreateServRequest.cluster());
    assertEquals(0, seenCreateServRequest.tags().size());
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
    verify(mockEcsV2).registerTaskDefinition(registerTaskDefArgs.capture());
    RegisterTaskDefinitionRequest seenTaskDefRequest = registerTaskDefArgs.getValue();
    assertEquals(expectedServerGroupName, seenTaskDefRequest.family());
    assertEquals(1, seenTaskDefRequest.containerDefinitions().size());
    assertEquals("aws-vpc", seenTaskDefRequest.networkModeAsString());

    ArgumentCaptor<DescribeTargetGroupsRequest> elbArgCaptor =
        ArgumentCaptor.forClass(DescribeTargetGroupsRequest.class);
    verify(mockELB).describeTargetGroups(elbArgCaptor.capture());

    ArgumentCaptor<CreateServiceRequest> createServiceArgs =
        ArgumentCaptor.forClass(CreateServiceRequest.class);
    verify(mockEcsV2).createService(createServiceArgs.capture());
    CreateServiceRequest seenCreateServRequest = createServiceArgs.getValue();
    assertEquals(expectedServerGroupName + "-v000", seenCreateServRequest.serviceName());
    assertEquals(1, seenCreateServRequest.loadBalancers().size());
    assertEquals("FARGATE", seenCreateServRequest.launchTypeAsString());
    // assert network stuff is set
    LoadBalancer serviceLB = seenCreateServRequest.loadBalancers().get(0);
    assertEquals("main", serviceLB.containerName());
    assertEquals(80, serviceLB.containerPort().intValue());
    assertEquals("integInputsFargateTgMappings-cluster", seenCreateServRequest.cluster());
    assertEquals(0, seenCreateServRequest.tags().size());
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
    // when
    Mockito.doThrow(InvalidParameterException.builder().message("Something is wrong.").build())
        .when(mockEcsV2)
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
    verify(mockEcsV2).registerTaskDefinition(registerTaskDefArgs.capture());
    RegisterTaskDefinitionRequest seenTaskDefRequest = registerTaskDefArgs.getValue();
    assertEquals(expectedServerGroupName, seenTaskDefRequest.family());
    assertEquals(1, seenTaskDefRequest.containerDefinitions().size());

    ArgumentCaptor<CreateServiceRequest> createServiceArgs =
        ArgumentCaptor.forClass(CreateServiceRequest.class);
    verify(mockEcsV2).createService(createServiceArgs.capture());
    CreateServiceRequest seenCreateServRequest = createServiceArgs.getValue();
    assertEquals("EC2", seenCreateServRequest.launchTypeAsString());
    assertEquals(expectedServerGroupName + "-v000", seenCreateServRequest.serviceName());
    assertEquals(0, seenCreateServRequest.tags().size());
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
    verify(mockEcsV2).registerTaskDefinition(registerTaskDefArgs.capture());
    RegisterTaskDefinitionRequest seenTaskDefRequest = registerTaskDefArgs.getValue();
    assertEquals(expectedServerGroupName, seenTaskDefRequest.family());
    assertEquals(1, seenTaskDefRequest.containerDefinitions().size());

    ArgumentCaptor<CreateServiceRequest> createServiceArgs =
        ArgumentCaptor.forClass(CreateServiceRequest.class);
    verify(mockEcsV2).createService(createServiceArgs.capture());
    CreateServiceRequest seenCreateServRequest = createServiceArgs.getValue();
    assertEquals("EC2", seenCreateServRequest.launchTypeAsString());
    assertEquals(expectedServerGroupName + "-v000", seenCreateServRequest.serviceName());
    assertEquals(80, seenCreateServRequest.serviceRegistries().get(0).containerPort());
    assertEquals(
        "arn:aws:servicediscovery:us-west-2:910995322324:service/srv-ckeydmrhzmqh6yfz",
        seenCreateServRequest.serviceRegistries().get(0).registryArn());
    assertEquals(
        true, seenCreateServRequest.serviceRegistries().get(0).containerName().contains("v000"));
    assertEquals(0, seenCreateServRequest.tags().size());
  }
}
