/*
 * Copyright 2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.aws.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateRequest;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateResult;
import com.amazonaws.services.ec2.model.DescribeAddressesResult;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeKeyPairsResult;
import com.amazonaws.services.ec2.model.DescribeLaunchTemplatesRequest;
import com.amazonaws.services.ec2.model.DescribeLaunchTemplatesResult;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.DescribeVpcClassicLinkResult;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.services.ec2.model.LaunchTemplate;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Tag;
import com.netflix.spinnaker.clouddriver.aws.AwsBaseSpec;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.utils.TestUtils;
import io.restassured.http.ContentType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test class with launch template settings enabled in clouddriver.yml, for CreateServerGroup
 * operation.
 */
@ActiveProfiles("launch-templates")
public class CreateServerGroupMixedInstancesPolicySpec extends AwsBaseSpec {
  @Value("${aws.features.launch-templates.enabled}")
  private Boolean AWS_LAUNCH_TEMPLATES_ENABLED;

  @Value("${aws.features.launch-templates.allowed-applications}")
  private String AWS_LAUNCH_TEMPLATES_ALLOWED_APPS;

  private final String EXPECTED_SERVER_GROUP_NAME = "myAwsApp-myStack-v000";
  private final LaunchTemplateSpecification EXPECTED_LAUNCH_TEMPLATE_SPEC =
      new LaunchTemplateSpecification().withLaunchTemplateId("lt-1").withVersion("$Latest");
  private final String EXPECTED_DEPLOY_WITH_MIP_MSG_FMT =
      "Deploying ASG %s with mixed instances policy";

  private AmazonAutoScaling mockAutoScaling = mock(AmazonAutoScaling.class);
  private AmazonEC2 mockEc2 = mock(AmazonEC2.class);

  @BeforeEach
  public void setup() {
    // mock EC2 responses
    when(mockRegionScopedProvider.getAmazonEC2()).thenReturn(mockEc2);
    when(mockAwsClientProvider.getAmazonEC2(
            any(NetflixAmazonCredentials.class), anyString(), anyBoolean()))
        .thenReturn(mockEc2);

    when(mockEc2.describeSecurityGroups(any(DescribeSecurityGroupsRequest.class)))
        .thenReturn(
            new DescribeSecurityGroupsResult()
                .withSecurityGroups(
                    new SecurityGroup().withGroupId("sg-123").withGroupName("myAwsApp")));
    when(mockEc2.describeVpcClassicLink()).thenReturn(new DescribeVpcClassicLinkResult());
    when(mockEc2.describeAddresses()).thenReturn(new DescribeAddressesResult());
    when(mockEc2.describeVpcs()).thenReturn(new DescribeVpcsResult());
    when(mockEc2.describeKeyPairs()).thenReturn(new DescribeKeyPairsResult());
    when(mockEc2.describeInstances(any(DescribeInstancesRequest.class)))
        .thenReturn(new DescribeInstancesResult());
    when(mockEc2.describeImages(any(DescribeImagesRequest.class)))
        .thenReturn(new DescribeImagesResult().withImages(new Image().withImageId("ami-12345")));
    when(mockEc2.describeSubnets())
        .thenReturn(
            new DescribeSubnetsResult()
                .withSubnets(
                    Arrays.asList(
                        new Subnet()
                            .withSubnetId("subnetId1")
                            .withAvailabilityZone("us-west-1a")
                            .withTags(
                                new Tag()
                                    .withKey("immutable_metadata")
                                    .withValue(
                                        "{\"purpose\": \"internal\", \"target\": \"ec2\" }")),
                        new Subnet()
                            .withSubnetId("subnetId2")
                            .withAvailabilityZone("us-west-2a"))));

    when(mockEc2.describeLaunchTemplates(any(DescribeLaunchTemplatesRequest.class)))
        .thenReturn(
            new DescribeLaunchTemplatesResult()
                .withLaunchTemplates(
                    Arrays.asList(
                        new LaunchTemplate()
                            .withLaunchTemplateId("lt-1")
                            .withLatestVersionNumber(1L)
                            .withDefaultVersionNumber(0L),
                        new LaunchTemplate()
                            .withLaunchTemplateId("lt-2")
                            .withLatestVersionNumber(1L)
                            .withDefaultVersionNumber(0L))));

    when(mockEc2.createLaunchTemplate(any(CreateLaunchTemplateRequest.class)))
        .thenReturn(
            new CreateLaunchTemplateResult()
                .withLaunchTemplate(
                    new LaunchTemplate().withLaunchTemplateId("lt-1").withLatestVersionNumber(1L)));

    // mock autoscaling response
    when(mockAwsClientProvider.getAutoScaling(any(NetflixAmazonCredentials.class), anyString()))
        .thenReturn(mockAutoScaling);
    when(mockAwsClientProvider.getAutoScaling(
            any(NetflixAmazonCredentials.class), anyString(), anyBoolean()))
        .thenReturn(mockAutoScaling);
    when(mockAutoScaling.describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class)))
        .thenReturn(new DescribeAutoScalingGroupsResult());
  }

  @DisplayName("Assert AWS and launch template features are enabled")
  @Test
  public void configTest() {
    assertTrue(AWS_ENABLED);
    assertEquals("aws-account1", AWS_ACCOUNT_NAME);

    // launch templates need to be enabled to use AWS ASG MixedInstancesPolicy
    assertTrue(AWS_LAUNCH_TEMPLATES_ENABLED);
    assertEquals("myAwsApp:aws-account1:us-west-1", AWS_LAUNCH_TEMPLATES_ALLOWED_APPS);
  }

  @DisplayName(
      "Given request for server group with instances distribution features, "
          + "successfully submit createServerGroup operation with mixed instances policy")
  @Test
  public void createServerGroup_instancesDistribution_used_expect_mixedInstancesPolicy()
      throws InterruptedException {
    // given
    Map<String, Object> requestBody =
        TestUtils.loadJson(PATH_PREFIX + "createServerGroup-basic.json")
            .withValue("setLaunchTemplate", true)
            .withValue("onDemandBaseCapacity", 1)
            .withValue("onDemandPercentageAboveBaseCapacity", 50)
            .withValue("spotAllocationStrategy", "capacity-optimized")
            .withValue("spotPrice", "1.5")
            .withValue("instanceType", "c3.large")
            .withValue("securityGroup", new String[] {"myAwsApp"})
            .asMap();

    // when, then
    String taskId =
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post(getBaseUrl() + CREATE_SERVER_GROUP_OP_PATH)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("resourceUri", containsString("/task/"))
            .extract()
            .path("id");

    // then
    final String taskHistory = getTaskUpdatesAfterCompletion(taskId);
    assertTrue(
        taskHistory.contains(
            String.format(EXPECTED_DEPLOY_WITH_MIP_MSG_FMT, EXPECTED_SERVER_GROUP_NAME)));
    assertTrue(taskHistory.contains(EXPECTED_DEPLOY_SUCCESS_MSG));

    // capture and assert arguments
    ArgumentCaptor<CreateLaunchTemplateRequest> createLaunchTemplateArgs =
        ArgumentCaptor.forClass(CreateLaunchTemplateRequest.class);
    verify(mockEc2).createLaunchTemplate(createLaunchTemplateArgs.capture());
    CreateLaunchTemplateRequest createLtReq = createLaunchTemplateArgs.getValue();
    assertTrue(createLtReq.getLaunchTemplateName().contains("myAwsApp-myStack-v000-"));
    assertEquals("c3.large", createLtReq.getLaunchTemplateData().getInstanceType());
    assertEquals(
        1, createLtReq.getLaunchTemplateData().getNetworkInterfaces().get(0).getGroups().size());
    assertEquals(
        "sg-123",
        createLtReq.getLaunchTemplateData().getNetworkInterfaces().get(0).getGroups().get(0));
    ;

    ArgumentCaptor<CreateAutoScalingGroupRequest> createAsgArgs =
        ArgumentCaptor.forClass(CreateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).createAutoScalingGroup(createAsgArgs.capture());
    CreateAutoScalingGroupRequest createAsgReq = createAsgArgs.getValue();
    assertNull(createAsgReq.getLaunchTemplate());
    assertEquals(
        EXPECTED_LAUNCH_TEMPLATE_SPEC,
        createAsgReq
            .getMixedInstancesPolicy()
            .getLaunchTemplate()
            .getLaunchTemplateSpecification());
    assertEquals(
        "{OnDemandBaseCapacity: 1,OnDemandPercentageAboveBaseCapacity: 50,SpotAllocationStrategy: capacity-optimized,SpotMaxPrice: 1.5}",
        createAsgReq.getMixedInstancesPolicy().getInstancesDistribution().toString());
  }

  @DisplayName(
      "Given request for server group with multiple instance types / launch template overrides, "
          + "successfully submit createServerGroup operation with mixed instances policy")
  @Test
  public void createServerGroup_multiInstanceTypes_used_expect_mixedInstancesPolicy()
      throws InterruptedException {
    // given
    Map<String, Object> requestBody =
        TestUtils.loadJson(PATH_PREFIX + "createServerGroup-basic.json")
            .withValue("setLaunchTemplate", true)
            .withValue(
                "launchTemplateOverridesForInstanceType",
                List.of(
                    Map.of("instanceType", "t3.large", "weightedCapacity", "1"),
                    Map.of("instanceType", "c3.large", "weightedCapacity", "1"),
                    Map.of("instanceType", "c3.xlarge", "weightedCapacity", "2")))
            .asMap();

    // when, then
    String taskId =
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post(getBaseUrl() + CREATE_SERVER_GROUP_OP_PATH)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("resourceUri", containsString("/task/"))
            .extract()
            .path("id");

    // then
    final String taskHistory = getTaskUpdatesAfterCompletion(taskId);
    assertTrue(
        taskHistory.contains(
            String.format(EXPECTED_DEPLOY_WITH_MIP_MSG_FMT, EXPECTED_SERVER_GROUP_NAME)));
    assertTrue(taskHistory.contains(EXPECTED_DEPLOY_SUCCESS_MSG));

    // capture and assert arguments
    ArgumentCaptor<CreateLaunchTemplateRequest> createLaunchTemplateArgs =
        ArgumentCaptor.forClass(CreateLaunchTemplateRequest.class);
    verify(mockEc2).createLaunchTemplate(createLaunchTemplateArgs.capture());
    CreateLaunchTemplateRequest createLtReq = createLaunchTemplateArgs.getValue();
    assertTrue(createLtReq.getLaunchTemplateName().contains("myAwsApp-myStack-v000-"));
    assertEquals("c3.large", createLtReq.getLaunchTemplateData().getInstanceType());
    assertEquals(
        1, createLtReq.getLaunchTemplateData().getNetworkInterfaces().get(0).getGroups().size());
    assertEquals(
        "sg-123",
        createLtReq.getLaunchTemplateData().getNetworkInterfaces().get(0).getGroups().get(0));
    ;

    ArgumentCaptor<CreateAutoScalingGroupRequest> createAsgArgs =
        ArgumentCaptor.forClass(CreateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).createAutoScalingGroup(createAsgArgs.capture());
    CreateAutoScalingGroupRequest createAsgReq = createAsgArgs.getValue();
    assertNull(createAsgReq.getLaunchTemplate());
    assertEquals(
        EXPECTED_LAUNCH_TEMPLATE_SPEC,
        createAsgReq
            .getMixedInstancesPolicy()
            .getLaunchTemplate()
            .getLaunchTemplateSpecification());
    assertEquals(
        "[{InstanceType: t3.large,WeightedCapacity: 1,}, {InstanceType: c3.large,WeightedCapacity: 1,}, {InstanceType: c3.xlarge,WeightedCapacity: 2,}]",
        createAsgReq.getMixedInstancesPolicy().getLaunchTemplate().getOverrides().toString());
    assertEquals(
        "{}", createAsgReq.getMixedInstancesPolicy().getInstancesDistribution().toString());
  }
}
