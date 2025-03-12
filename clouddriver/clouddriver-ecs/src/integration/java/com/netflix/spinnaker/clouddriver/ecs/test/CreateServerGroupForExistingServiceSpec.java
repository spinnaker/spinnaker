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
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.EcsSpec;
import io.restassured.http.ContentType;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

public class CreateServerGroupForExistingServiceSpec extends EcsSpec {

  private AWSApplicationAutoScalingClient mockAWSApplicationAutoScalingClient =
      mock(AWSApplicationAutoScalingClient.class);

  private AmazonECS mockECS = mock(AmazonECS.class);

  private AmazonElasticLoadBalancing mockELB = mock(AmazonElasticLoadBalancing.class);

  @BeforeEach
  public void setup() {

    // mocking calls
    when(mockECS.listAccountSettings(any(ListAccountSettingsRequest.class)))
        .thenReturn(new ListAccountSettingsResult());
    when(mockECS.describeServices(any(DescribeServicesRequest.class)))
        .thenReturn(
            new DescribeServicesResult()
                .withServices(
                    Collections.singletonList(
                        new Service()
                            .withServiceName("ecs-integInputEC2TgMappingsExistingServiceStack-v000")
                            .withCreatedAt(new Date())
                            .withStatus("INACTIVE"))));

    when(mockECS.createService(any(CreateServiceRequest.class)))
        .thenReturn(new CreateServiceResult().withService(new Service()));

    when(mockECS.registerTaskDefinition(any(RegisterTaskDefinitionRequest.class)))
        .thenAnswer(
            (Answer<RegisterTaskDefinitionResult>)
                invocation -> {
                  RegisterTaskDefinitionRequest request =
                      (RegisterTaskDefinitionRequest) invocation.getArguments()[0];
                  String testArn = "arn:aws:ecs:::task-definition/" + request.getFamily() + ":2";
                  TaskDefinition taskDef = new TaskDefinition().withTaskDefinitionArn(testArn);
                  return new RegisterTaskDefinitionResult().withTaskDefinition(taskDef);
                });

    when(mockECS.listServices(any(ListServicesRequest.class)))
        .thenReturn(
            new ListServicesResult()
                .withServiceArns(
                    Collections.singletonList(
                        "arn:aws:ecs:ecs-integInputEC2TgMappingsExistingServiceStack-v000")));

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

    when(mockAwsProvider.getAmazonElasticLoadBalancingV2(
            any(NetflixAmazonCredentials.class), anyString(), anyBoolean()))
        .thenReturn(mockELB);
  }

  @DisplayName(
      ".\n===\n"
          + "Given description w/ task def input, EC2 launch type, and new target group "
          + "fields with the existing service, successfully submit createServerGroup operation"
          + "\n===")
  @Test
  public void createServerGroup_inputsEC2TgMappingsExistingServiceTest()
      throws IOException, InterruptedException {

    // given
    String url = getTestUrl(CREATE_SG_TEST_PATH);
    String requestBody =
        generateStringFromTestFile(
            "/createServerGroup-input-EC2-targetGroupMappings-existingService.json");
    String expectedServerGroupName = "ecs-integInputEC2TgMappingsExistingServiceStack-v001";

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
    assertEquals(expectedServerGroupName, seenTaskDefRequest.getFamily() + "-v001");
    assertEquals(1, seenTaskDefRequest.getContainerDefinitions().size());
    assertEquals("v001", seenTaskDefRequest.getContainerDefinitions().get(0).getName());

    ArgumentCaptor<DescribeTargetGroupsRequest> elbArgCaptor =
        ArgumentCaptor.forClass(DescribeTargetGroupsRequest.class);
    verify(mockELB).describeTargetGroups(elbArgCaptor.capture());
    DescribeTargetGroupsRequest seenTargetGroupRequest = elbArgCaptor.getValue();

    assertTrue(
        seenTargetGroupRequest
            .getNames()
            .contains("integInputEC2TgMappingsExistingService-targetGroup"));

    ArgumentCaptor<CreateServiceRequest> createServiceArgs =
        ArgumentCaptor.forClass(CreateServiceRequest.class);
    verify(mockECS).createService(createServiceArgs.capture());
    CreateServiceRequest seenCreateServRequest = createServiceArgs.getValue();
    assertEquals("EC2", seenCreateServRequest.getLaunchType());
    assertEquals(expectedServerGroupName, seenCreateServRequest.getServiceName());
    assertEquals(1, seenCreateServRequest.getLoadBalancers().size());
    LoadBalancer serviceLB = seenCreateServRequest.getLoadBalancers().get(0);
    assertEquals("v001", serviceLB.getContainerName());
    assertEquals(80, serviceLB.getContainerPort().intValue());
    assertEquals(
        "integInputEC2TgMappingsExistingService-cluster", seenCreateServRequest.getCluster());
    assertEquals(
        "arn:aws:elasticloadbalancing:::targetgroup/integInputEC2TgMappingsExistingService-targetGroup/76tgredfc",
        serviceLB.getTargetGroupArn());
  }
}
