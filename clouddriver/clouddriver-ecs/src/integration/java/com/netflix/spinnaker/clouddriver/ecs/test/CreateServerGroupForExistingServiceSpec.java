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

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.EcsSpec;
import io.restassured.http.ContentType;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroup;

public class CreateServerGroupForExistingServiceSpec extends EcsSpec {

  private EcsClient mockEcsV2 = mock(EcsClient.class);

  private ElasticLoadBalancingV2Client mockELB = mock(ElasticLoadBalancingV2Client.class);

  @BeforeEach
  public void setup() {

    // mock v2 ECS responses (used by EcsServerGroupNameResolver)
    when(mockEcsV2.listServices(any(ListServicesRequest.class)))
        .thenReturn(
            ListServicesResponse.builder()
                .serviceArns(
                    Collections.singletonList(
                        "arn:aws:ecs:ecs-integInputEC2TgMappingsExistingServiceStack-v000"))
                .build());
    when(mockEcsV2.describeServices(any(DescribeServicesRequest.class)))
        .thenReturn(
            DescribeServicesResponse.builder()
                .services(
                    Collections.singletonList(
                        Service.builder()
                            .serviceName("ecs-integInputEC2TgMappingsExistingServiceStack-v000")
                            .createdAt(Instant.now())
                            .status("INACTIVE")
                            .build()))
                .build());

    when(mockEcsV2.listAccountSettings(any(ListAccountSettingsRequest.class)))
        .thenReturn(ListAccountSettingsResponse.builder().build());

    when(mockEcsV2.registerTaskDefinition(any(RegisterTaskDefinitionRequest.class)))
        .thenAnswer(
            (Answer<RegisterTaskDefinitionResponse>)
                invocation -> {
                  RegisterTaskDefinitionRequest request = invocation.getArgument(0);
                  String testArn = "arn:aws:ecs:::task-definition/" + request.family() + ":2";
                  return RegisterTaskDefinitionResponse.builder()
                      .taskDefinition(TaskDefinition.builder().taskDefinitionArn(testArn).build())
                      .build();
                });

    when(mockEcsV2.createService(any(CreateServiceRequest.class)))
        .thenReturn(CreateServiceResponse.builder().service(Service.builder().build()).build());

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
    verify(mockEcsV2).registerTaskDefinition(registerTaskDefArgs.capture());
    RegisterTaskDefinitionRequest seenTaskDefRequest = registerTaskDefArgs.getValue();
    assertEquals(expectedServerGroupName, seenTaskDefRequest.family() + "-v001");
    assertEquals(1, seenTaskDefRequest.containerDefinitions().size());
    assertEquals("v001", seenTaskDefRequest.containerDefinitions().get(0).name());

    ArgumentCaptor<DescribeTargetGroupsRequest> elbArgCaptor =
        ArgumentCaptor.forClass(DescribeTargetGroupsRequest.class);
    verify(mockELB).describeTargetGroups(elbArgCaptor.capture());
    DescribeTargetGroupsRequest seenTargetGroupRequest = elbArgCaptor.getValue();

    assertTrue(
        seenTargetGroupRequest
            .names()
            .contains("integInputEC2TgMappingsExistingService-targetGroup"));

    ArgumentCaptor<CreateServiceRequest> createServiceArgs =
        ArgumentCaptor.forClass(CreateServiceRequest.class);
    verify(mockEcsV2).createService(createServiceArgs.capture());
    CreateServiceRequest seenCreateServRequest = createServiceArgs.getValue();
    assertEquals("EC2", seenCreateServRequest.launchTypeAsString());
    assertEquals(expectedServerGroupName, seenCreateServRequest.serviceName());
    assertEquals(1, seenCreateServRequest.loadBalancers().size());
    LoadBalancer serviceLB = seenCreateServRequest.loadBalancers().get(0);
    assertEquals("v001", serviceLB.containerName());
    assertEquals(80, serviceLB.containerPort().intValue());
    assertEquals("integInputEC2TgMappingsExistingService-cluster", seenCreateServRequest.cluster());
    assertEquals(
        "arn:aws:elasticloadbalancing:::targetgroup/integInputEC2TgMappingsExistingService-targetGroup/76tgredfc",
        serviceLB.targetGroupArn());
  }
}
