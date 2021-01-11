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
import static org.mockito.Mockito.*;

import com.amazonaws.services.applicationautoscaling.AWSApplicationAutoScalingClient;
import com.amazonaws.services.applicationautoscaling.model.*;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.model.*;
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
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

public class CreateServerGroupWithAppAutoScalingSpec extends EcsSpec {

  private AmazonECS mockECS = mock(AmazonECS.class);
  private AmazonElasticLoadBalancing mockELB = mock(AmazonElasticLoadBalancing.class);
  private AWSApplicationAutoScalingClient mockAWSApplicationAutoScalingClient =
      mock(AWSApplicationAutoScalingClient.class);
  private AmazonCloudWatch mockAmazonCloudWatchClient = mock(AmazonCloudWatch.class);

  @BeforeEach
  public void setup() {
    // mock ECS responses
    when(mockECS.listAccountSettings(any(ListAccountSettingsRequest.class)))
        .thenReturn(new ListAccountSettingsResult());
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

    when(mockAWSApplicationAutoScalingClient.describeScalableTargets(
            any(DescribeScalableTargetsRequest.class)))
        .thenAnswer(
            (Answer<DescribeScalableTargetsResult>)
                invocation -> {
                  ScalableTarget scalableTarget =
                      new ScalableTarget()
                          .withMaxCapacity(1)
                          .withMinCapacity(1)
                          .withResourceId("service/default/sample-webapp");
                  return new DescribeScalableTargetsResult().withScalableTargets(scalableTarget);
                });

    when(mockAWSApplicationAutoScalingClient.describeScalingPolicies(
            any(DescribeScalingPoliciesRequest.class)))
        .thenAnswer(
            (Answer<DescribeScalingPoliciesResult>)
                invocation -> {
                  Alarm alarm =
                      new Alarm()
                          .withAlarmARN("arn:aws:cloudwatch:us-east-1:123456789012:alarm:testAlarm")
                          .withAlarmName("testAlarm");
                  ScalingPolicy scalablePolicy =
                      new ScalingPolicy()
                          .withResourceId("service/default/sample-webapp")
                          .withPolicyName("ecsTestPolicy")
                          .withPolicyARN(
                              "arn:aws:autoscaling:us-west-2:012345678910:scalingPolicy:6d8972f3-efc8-437c-92d1-6270f29a66e7:resource/ecs/service/default/web-app:policyName/web-app-cpu-gt-75")
                          .withAlarms(Arrays.asList(alarm));
                  return new DescribeScalingPoliciesResult()
                      .withScalingPolicies(Arrays.asList(scalablePolicy));
                });

    when(mockAWSApplicationAutoScalingClient.putScalingPolicy(any(PutScalingPolicyRequest.class)))
        .thenReturn(
            new PutScalingPolicyResult()
                .withPolicyARN(
                    "arn:aws:autoscaling:us-west-2:012345678910:scalingPolicy:6d8972f3-efc8-437c-92d1-6270f29a66e7:resource/ecs/service/default/web-app:policyName/web-app-cpu-gt-75"));

    when(mockAmazonCloudWatchClient.describeAlarms(any(DescribeAlarmsRequest.class)))
        .thenReturn(
            new DescribeAlarmsResult()
                .withMetricAlarms(Arrays.asList(new MetricAlarm().withAlarmName("testAlarm"))));

    when(mockAwsProvider.getAmazonApplicationAutoScaling(
            any(NetflixAmazonCredentials.class), anyString(), anyBoolean()))
        .thenReturn(mockAWSApplicationAutoScalingClient);

    when(mockAwsProvider.getAmazonCloudWatch(
            any(NetflixAmazonCredentials.class), anyString(), anyBoolean()))
        .thenReturn(mockAmazonCloudWatchClient);

    when(mockAwsProvider.getAmazonElasticLoadBalancingV2(
            any(NetflixAmazonCredentials.class), anyString(), anyBoolean()))
        .thenReturn(mockELB);
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
    DescribeTargetGroupsRequest seenDescribeTargetGroups = elbArgCaptor.getValue();

    assertEquals(
        "integInputsEc2TargetGroupMappingsWithAppAutoScalingGroup-targetGroup",
        seenDescribeTargetGroups.getNames().get(0));

    ArgumentCaptor<CreateServiceRequest> createServiceArgs =
        ArgumentCaptor.forClass(CreateServiceRequest.class);
    verify(mockECS).createService(createServiceArgs.capture());
    CreateServiceRequest seenCreateServRequest = createServiceArgs.getValue();
    assertEquals("EC2", seenCreateServRequest.getLaunchType());
    assertEquals(expectedServerGroupName + "-v000", seenCreateServRequest.getServiceName());
    assertEquals(1, seenCreateServRequest.getLoadBalancers().size());
    assertEquals(
        "integInputsEc2TargetGroupMappingsWithAppAutoScaling-cluster",
        seenCreateServRequest.getCluster());
    LoadBalancer serviceLB = seenCreateServRequest.getLoadBalancers().get(0);
    assertEquals("v000", serviceLB.getContainerName());
    assertEquals(80, serviceLB.getContainerPort().intValue());

    ArgumentCaptor<DescribeAlarmsRequest> describeAlarmsRequestArgsCaptor =
        ArgumentCaptor.forClass(DescribeAlarmsRequest.class);
    verify(mockAmazonCloudWatchClient, atLeast(1))
        .describeAlarms(describeAlarmsRequestArgsCaptor.capture());

    assertTrue(
        describeAlarmsRequestArgsCaptor.getAllValues().stream()
            .anyMatch(alarm -> alarm.getAlarmNames().contains("testAlarm")));

    ArgumentCaptor<DescribeScalingPoliciesRequest> describeScalingPoliciesRequestArgumentCaptor =
        ArgumentCaptor.forClass(DescribeScalingPoliciesRequest.class);
    verify(mockAWSApplicationAutoScalingClient)
        .describeScalingPolicies(describeScalingPoliciesRequestArgumentCaptor.capture());
    DescribeScalingPoliciesRequest seenDescribePoliciesRequest =
        describeScalingPoliciesRequestArgumentCaptor.getValue();

    assertEquals("service/default/sample-webapp", seenDescribePoliciesRequest.getResourceId());

    ArgumentCaptor<DescribeScalableTargetsRequest> describeScalableTargetsRequestArgumentCaptor =
        ArgumentCaptor.forClass(DescribeScalableTargetsRequest.class);
    verify(mockAWSApplicationAutoScalingClient, atLeast(1))
        .describeScalableTargets(describeScalableTargetsRequestArgumentCaptor.capture());

    assertTrue(
        describeScalableTargetsRequestArgumentCaptor.getAllValues().stream()
            .anyMatch(
                scalabletarget ->
                    ("ecs:service:DesiredCount").equals(scalabletarget.getScalableDimension())));

    ArgumentCaptor<PutScalingPolicyRequest> putScalingPolicyRequestArgumentCaptor =
        ArgumentCaptor.forClass(PutScalingPolicyRequest.class);
    verify(mockAWSApplicationAutoScalingClient)
        .putScalingPolicy(putScalingPolicyRequestArgumentCaptor.capture());
    PutScalingPolicyRequest seenPutScalingPolicyRequest =
        putScalingPolicyRequestArgumentCaptor.getValue();
    assertEquals("createdServiceTestPolicy", seenPutScalingPolicyRequest.getPolicyName());
    assertEquals(
        "service/integInputsEc2TargetGroupMappingsWithAppAutoScaling-cluster/createdService",
        seenPutScalingPolicyRequest.getResourceId());
  }
}
