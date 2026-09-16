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
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import software.amazon.awssdk.services.applicationautoscaling.ApplicationAutoScalingClient;
import software.amazon.awssdk.services.applicationautoscaling.model.Alarm;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalingPoliciesRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalingPoliciesResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.PutScalingPolicyRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.PutScalingPolicyResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.ScalableTarget;
import software.amazon.awssdk.services.applicationautoscaling.model.ScalingPolicy;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsRequest;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsResponse;
import software.amazon.awssdk.services.cloudwatch.model.MetricAlarm;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroup;

public class CreateServerGroupWithAppAutoScalingSpec extends EcsSpec {

  private EcsClient mockEcsV2 = mock(EcsClient.class);
  private ElasticLoadBalancingV2Client mockELB = mock(ElasticLoadBalancingV2Client.class);
  private ApplicationAutoScalingClient mockAutoScalingV2 = mock(ApplicationAutoScalingClient.class);
  private CloudWatchClient mockAmazonCloudWatchClient = mock(CloudWatchClient.class);

  @BeforeEach
  public void setup() {
    // mock v2 ECS responses (used by EcsServerGroupNameResolver + source lookup)
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
    when(mockAwsProvider.getElasticLoadBalancingV2Client(
            any(NetflixAmazonCredentials.class), anyString()))
        .thenReturn(mockELB);

    // mock v2 Application Auto Scaling (source scalable target + register scalable target)
    when(mockAutoScalingV2.describeScalableTargets(any(DescribeScalableTargetsRequest.class)))
        .thenReturn(
            DescribeScalableTargetsResponse.builder()
                .scalableTargets(
                    ScalableTarget.builder()
                        .maxCapacity(1)
                        .minCapacity(1)
                        .resourceId("service/default/sample-webapp")
                        .build())
                .build());
    when(mockAwsProvider.getAmazonApplicationAutoScalingV2(
            any(NetflixAmazonCredentials.class), anyString()))
        .thenReturn(mockAutoScalingV2);

    // mock Application Auto Scaling + CloudWatch used by
    // EcsCloudMetricService.copyScalingPolicies
    when(mockAutoScalingV2.describeScalingPolicies(any(DescribeScalingPoliciesRequest.class)))
        .thenAnswer(
            (Answer<DescribeScalingPoliciesResponse>)
                invocation -> {
                  Alarm alarm =
                      Alarm.builder()
                          .alarmARN("arn:aws:cloudwatch:us-east-1:123456789012:alarm:testAlarm")
                          .alarmName("testAlarm")
                          .build();
                  ScalingPolicy scalablePolicy =
                      ScalingPolicy.builder()
                          .resourceId("service/default/sample-webapp")
                          .policyName("ecsTestPolicy")
                          .policyARN(
                              "arn:aws:autoscaling:us-west-2:012345678910:scalingPolicy:6d8972f3-efc8-437c-92d1-6270f29a66e7:resource/ecs/service/default/web-app:policyName/web-app-cpu-gt-75")
                          .alarms(Arrays.asList(alarm))
                          .build();
                  return DescribeScalingPoliciesResponse.builder()
                      .scalingPolicies(Arrays.asList(scalablePolicy))
                      .build();
                });

    when(mockAutoScalingV2.putScalingPolicy(any(PutScalingPolicyRequest.class)))
        .thenReturn(
            PutScalingPolicyResponse.builder()
                .policyARN(
                    "arn:aws:autoscaling:us-west-2:012345678910:scalingPolicy:6d8972f3-efc8-437c-92d1-6270f29a66e7:resource/ecs/service/default/web-app:policyName/web-app-cpu-gt-75")
                .build());

    when(mockAmazonCloudWatchClient.describeAlarms(any(DescribeAlarmsRequest.class)))
        .thenReturn(
            DescribeAlarmsResponse.builder()
                .metricAlarms(Arrays.asList(MetricAlarm.builder().alarmName("testAlarm").build()))
                .build());

    when(mockAwsProvider.getAmazonCloudWatchV2(any(NetflixAmazonCredentials.class), anyString()))
        .thenReturn(mockAmazonCloudWatchClient);
  }

  @DisplayName(
      ".\n===\n"
          + "Given description w/ inputs, EC2 launch type, and target group mappings "
          + "with Application Auto Scaling, successfully submit createServerGroup operation"
          + "\n===")
  @Test
  public void createServerGroup_InputsEc2TargetGroupMappingsWithAppAutoScalingGroupTest()
      throws IOException, InterruptedException {

    // given
    String url = getTestUrl(CREATE_SG_TEST_PATH);
    String requestBody =
        generateStringFromTestFile(
            "/createServerGroup-inputs-ec2-targetGroupMappings-appAutoScalingGroup.json");
    String expectedServerGroupName = "ecs-integInputsEc2TargetGroupMappingsWithAppAutoScalingGroup";

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
          if (taskHistory.toString().contains("Done copying scaling policies...")) {
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
    DescribeTargetGroupsRequest seenDescribeTargetGroups = elbArgCaptor.getValue();

    assertEquals(
        "integInputsEc2TargetGroupMappingsWithAppAutoScalingGroup-targetGroup",
        seenDescribeTargetGroups.names().get(0));

    ArgumentCaptor<CreateServiceRequest> createServiceArgs =
        ArgumentCaptor.forClass(CreateServiceRequest.class);
    verify(mockEcsV2).createService(createServiceArgs.capture());
    CreateServiceRequest seenCreateServRequest = createServiceArgs.getValue();
    assertEquals("EC2", seenCreateServRequest.launchTypeAsString());
    assertEquals(expectedServerGroupName + "-v000", seenCreateServRequest.serviceName());
    assertEquals(1, seenCreateServRequest.loadBalancers().size());
    assertEquals(
        "integInputsEc2TargetGroupMappingsWithAppAutoScaling-cluster",
        seenCreateServRequest.cluster());
    LoadBalancer serviceLB = seenCreateServRequest.loadBalancers().get(0);
    assertEquals("v000", serviceLB.containerName());
    assertEquals(80, serviceLB.containerPort().intValue());

    ArgumentCaptor<DescribeAlarmsRequest> describeAlarmsRequestArgsCaptor =
        ArgumentCaptor.forClass(DescribeAlarmsRequest.class);
    verify(mockAmazonCloudWatchClient, atLeast(1))
        .describeAlarms(describeAlarmsRequestArgsCaptor.capture());

    assertTrue(
        describeAlarmsRequestArgsCaptor.getAllValues().stream()
            .anyMatch(alarm -> alarm.alarmNames().contains("testAlarm")));

    ArgumentCaptor<DescribeScalingPoliciesRequest> describeScalingPoliciesRequestArgumentCaptor =
        ArgumentCaptor.forClass(DescribeScalingPoliciesRequest.class);
    verify(mockAutoScalingV2)
        .describeScalingPolicies(describeScalingPoliciesRequestArgumentCaptor.capture());
    DescribeScalingPoliciesRequest seenDescribePoliciesRequest =
        describeScalingPoliciesRequestArgumentCaptor.getValue();

    assertEquals("service/default/sample-webapp", seenDescribePoliciesRequest.resourceId());

    ArgumentCaptor<DescribeScalableTargetsRequest> describeScalableTargetsRequestArgumentCaptor =
        ArgumentCaptor.forClass(DescribeScalableTargetsRequest.class);
    verify(mockAutoScalingV2, atLeast(1))
        .describeScalableTargets(describeScalableTargetsRequestArgumentCaptor.capture());

    assertTrue(
        describeScalableTargetsRequestArgumentCaptor.getAllValues().stream()
            .anyMatch(
                scalabletarget ->
                    ("ecs:service:DesiredCount")
                        .equals(scalabletarget.scalableDimensionAsString())));

    ArgumentCaptor<PutScalingPolicyRequest> putScalingPolicyRequestArgumentCaptor =
        ArgumentCaptor.forClass(PutScalingPolicyRequest.class);
    verify(mockAutoScalingV2).putScalingPolicy(putScalingPolicyRequestArgumentCaptor.capture());
    PutScalingPolicyRequest seenPutScalingPolicyRequest =
        putScalingPolicyRequestArgumentCaptor.getValue();
    assertEquals("createdServiceTestPolicy", seenPutScalingPolicyRequest.policyName());
    assertEquals(
        "service/integInputsEc2TargetGroupMappingsWithAppAutoScaling-cluster/createdService",
        seenPutScalingPolicyRequest.resourceId());
  }
}
