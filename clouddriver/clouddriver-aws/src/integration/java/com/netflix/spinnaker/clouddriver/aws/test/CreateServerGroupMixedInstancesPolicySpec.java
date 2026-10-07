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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import software.amazon.awssdk.services.autoscaling.AutoScalingClient;
import software.amazon.awssdk.services.autoscaling.model.CreateAutoScalingGroupRequest;
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsResponse;
import software.amazon.awssdk.services.autoscaling.model.LaunchTemplateSpecification;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.CreateLaunchTemplateRequest;
import software.amazon.awssdk.services.ec2.model.CreateLaunchTemplateResponse;
import software.amazon.awssdk.services.ec2.model.DescribeAddressesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeImagesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeImagesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceTypesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceTypesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeKeyPairsResponse;
import software.amazon.awssdk.services.ec2.model.DescribeLaunchTemplatesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeLaunchTemplatesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsResponse;
import software.amazon.awssdk.services.ec2.model.DescribeSubnetsResponse;
import software.amazon.awssdk.services.ec2.model.DescribeVpcClassicLinkResponse;
import software.amazon.awssdk.services.ec2.model.DescribeVpcsResponse;
import software.amazon.awssdk.services.ec2.model.Image;
import software.amazon.awssdk.services.ec2.model.InstanceTypeInfo;
import software.amazon.awssdk.services.ec2.model.LaunchTemplate;
import software.amazon.awssdk.services.ec2.model.ProcessorInfo;
import software.amazon.awssdk.services.ec2.model.SecurityGroup;
import software.amazon.awssdk.services.ec2.model.Subnet;
import software.amazon.awssdk.services.ec2.model.Tag;

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
      LaunchTemplateSpecification.builder().launchTemplateId("lt-1").version("$Latest").build();
  private final String EXPECTED_DEPLOY_WITH_MIP_MSG_FMT =
      "Deploying ASG %s with mixed instances policy";

  private AutoScalingClient mockAutoScaling = mock(AutoScalingClient.class);
  private Ec2Client mockEc2 = mock(Ec2Client.class);

  @BeforeEach
  public void setup() {
    // mock EC2 responses
    when(mockRegionScopedProvider.getAmazonEC2()).thenReturn(mockEc2);
    when(mockAwsClientProvider.getAmazonEC2V2(any(NetflixAmazonCredentials.class), anyString()))
        .thenReturn(mockEc2);

    when(mockEc2.describeSecurityGroups(any(DescribeSecurityGroupsRequest.class)))
        .thenReturn(
            DescribeSecurityGroupsResponse.builder()
                .securityGroups(
                    SecurityGroup.builder().groupId("sg-123").groupName("myAwsApp").build())
                .build());
    when(mockEc2.describeVpcClassicLink())
        .thenReturn(DescribeVpcClassicLinkResponse.builder().build());
    when(mockEc2.describeAddresses()).thenReturn(DescribeAddressesResponse.builder().build());
    when(mockEc2.describeVpcs()).thenReturn(DescribeVpcsResponse.builder().build());
    when(mockEc2.describeKeyPairs()).thenReturn(DescribeKeyPairsResponse.builder().build());
    when(mockEc2.describeInstances(any(DescribeInstancesRequest.class)))
        .thenReturn(DescribeInstancesResponse.builder().build());
    when(mockEc2.describeImages(any(DescribeImagesRequest.class)))
        .thenReturn(
            DescribeImagesResponse.builder()
                .images(
                    Image.builder()
                        .imageId("ami-12345")
                        .virtualizationType("hvm")
                        .architecture("x86_64")
                        .build())
                .build());
    when(mockEc2.describeInstanceTypes(any(DescribeInstanceTypesRequest.class)))
        .thenReturn(
            DescribeInstanceTypesResponse.builder()
                .instanceTypes(
                    InstanceTypeInfo.builder()
                        .instanceType("t3.medium")
                        .processorInfo(
                            ProcessorInfo.builder()
                                .supportedArchitecturesWithStrings("x86_64")
                                .build())
                        .supportedVirtualizationTypesWithStrings(Arrays.asList("hvm"))
                        .build(),
                    InstanceTypeInfo.builder()
                        .instanceType("c3.large")
                        .processorInfo(
                            ProcessorInfo.builder()
                                .supportedArchitecturesWithStrings("i386", "x86_64")
                                .build())
                        .supportedVirtualizationTypesWithStrings(
                            Arrays.asList("hvm", "paravirtual"))
                        .build(),
                    InstanceTypeInfo.builder()
                        .instanceType("c3.xlarge")
                        .processorInfo(
                            ProcessorInfo.builder()
                                .supportedArchitecturesWithStrings("x86_64")
                                .build())
                        .supportedVirtualizationTypesWithStrings(
                            Arrays.asList("hvm", "paravirtual"))
                        .build())
                .build());
    when(mockEc2.describeSubnets())
        .thenReturn(
            DescribeSubnetsResponse.builder()
                .subnets(
                    Arrays.asList(
                        Subnet.builder()
                            .subnetId("subnetId1")
                            .availabilityZone("us-west-1a")
                            .tags(
                                Tag.builder()
                                    .key("immutable_metadata")
                                    .value("{\"purpose\": \"internal\", \"target\": \"ec2\" }")
                                    .build())
                            .build(),
                        Subnet.builder()
                            .subnetId("subnetId2")
                            .availabilityZone("us-west-2a")
                            .build()))
                .build());

    when(mockEc2.describeLaunchTemplates(any(DescribeLaunchTemplatesRequest.class)))
        .thenReturn(
            DescribeLaunchTemplatesResponse.builder()
                .launchTemplates(
                    Arrays.asList(
                        LaunchTemplate.builder()
                            .launchTemplateId("lt-1")
                            .latestVersionNumber(1L)
                            .defaultVersionNumber(0L)
                            .build(),
                        LaunchTemplate.builder()
                            .launchTemplateId("lt-2")
                            .latestVersionNumber(1L)
                            .defaultVersionNumber(0L)
                            .build()))
                .build());

    when(mockEc2.createLaunchTemplate(any(CreateLaunchTemplateRequest.class)))
        .thenReturn(
            CreateLaunchTemplateResponse.builder()
                .launchTemplate(
                    LaunchTemplate.builder()
                        .launchTemplateId("lt-1")
                        .latestVersionNumber(1L)
                        .build())
                .build());

    // mock autoscaling response
    when(mockAwsClientProvider.getAutoScalingV2(any(NetflixAmazonCredentials.class), anyString()))
        .thenReturn(mockAutoScaling);
    when(mockAutoScaling.describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class)))
        .thenReturn(DescribeAutoScalingGroupsResponse.builder().build());
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
    assertTrue(createLtReq.launchTemplateName().contains("myAwsApp-myStack-v000-"));
    assertEquals("c3.large", createLtReq.launchTemplateData().instanceTypeAsString());
    assertEquals(1, createLtReq.launchTemplateData().networkInterfaces().get(0).groups().size());
    assertEquals(
        "sg-123", createLtReq.launchTemplateData().networkInterfaces().get(0).groups().get(0));
    ;

    ArgumentCaptor<CreateAutoScalingGroupRequest> createAsgArgs =
        ArgumentCaptor.forClass(CreateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).createAutoScalingGroup(createAsgArgs.capture());
    CreateAutoScalingGroupRequest createAsgReq = createAsgArgs.getValue();
    assertNull(createAsgReq.launchTemplate());
    assertEquals(
        EXPECTED_LAUNCH_TEMPLATE_SPEC,
        createAsgReq.mixedInstancesPolicy().launchTemplate().launchTemplateSpecification());
    assertEquals(
        "InstancesDistribution(OnDemandBaseCapacity=1, OnDemandPercentageAboveBaseCapacity=50, SpotAllocationStrategy=capacity-optimized, SpotMaxPrice=1.5)",
        createAsgReq.mixedInstancesPolicy().instancesDistribution().toString());
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
    assertTrue(createLtReq.launchTemplateName().contains("myAwsApp-myStack-v000-"));
    assertEquals("c3.large", createLtReq.launchTemplateData().instanceTypeAsString());
    assertEquals(1, createLtReq.launchTemplateData().networkInterfaces().get(0).groups().size());
    assertEquals(
        "sg-123", createLtReq.launchTemplateData().networkInterfaces().get(0).groups().get(0));
    ;

    ArgumentCaptor<CreateAutoScalingGroupRequest> createAsgArgs =
        ArgumentCaptor.forClass(CreateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).createAutoScalingGroup(createAsgArgs.capture());
    CreateAutoScalingGroupRequest createAsgReq = createAsgArgs.getValue();
    assertNull(createAsgReq.launchTemplate());
    assertEquals(
        EXPECTED_LAUNCH_TEMPLATE_SPEC,
        createAsgReq.mixedInstancesPolicy().launchTemplate().launchTemplateSpecification());
    assertEquals(
        "[LaunchTemplateOverrides(InstanceType=t3.large, WeightedCapacity=1), LaunchTemplateOverrides(InstanceType=c3.large, WeightedCapacity=1), LaunchTemplateOverrides(InstanceType=c3.xlarge, WeightedCapacity=2)]",
        createAsgReq.mixedInstancesPolicy().launchTemplate().overrides().toString());
    assertEquals(
        "InstancesDistribution()",
        createAsgReq.mixedInstancesPolicy().instancesDistribution().toString());
  }
}
