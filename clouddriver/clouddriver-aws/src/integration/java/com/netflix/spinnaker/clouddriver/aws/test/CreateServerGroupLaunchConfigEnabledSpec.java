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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsRequest;
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsResult;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeAddressesResult;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.DescribeInstanceTypesRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceTypesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeKeyPairsResult;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.DescribeVpcClassicLinkResult;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.services.ec2.model.InstanceTypeInfo;
import com.amazonaws.services.ec2.model.ProcessorInfo;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Tag;
import com.netflix.spinnaker.clouddriver.aws.AwsBaseSpec;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.utils.TestUtils;
import io.restassured.http.ContentType;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Value;

/**
 * Test class with launch configuration settings enabled in clouddriver.yml, for CreateServerGroup
 * operation.
 */
public class CreateServerGroupLaunchConfigEnabledSpec extends AwsBaseSpec {

  @Value("${aws.features.launch-templates:#{null}}")
  Boolean AWS_LAUNCH_TEMPLATES;

  private final String EXPECTED_SERVER_GROUP_NAME = "myAwsApp-myStack-v000";
  private final String EXPECTED_LAUNCH_CONFIG_NAME =
      EXPECTED_SERVER_GROUP_NAME + "-"; // partial name without the timestamp part
  private final String EXPECTED_DEPLOY_WITH_LC_MSG_FMT =
      "Deploying ASG %s with launch configuration %s";

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
        .thenReturn(
            new DescribeImagesResult()
                .withImages(
                    new Image()
                        .withImageId("ami-12345")
                        .withVirtualizationType("hvm")
                        .withArchitecture("x86_64")));
    when(mockEc2.describeInstanceTypes(any(DescribeInstanceTypesRequest.class)))
        .thenReturn(
            new DescribeInstanceTypesResult()
                .withInstanceTypes(
                    new InstanceTypeInfo()
                        .withInstanceType("c3.large")
                        .withProcessorInfo(
                            new ProcessorInfo().withSupportedArchitectures("i386", "x86_64"))
                        .withSupportedVirtualizationTypes(Arrays.asList("hvm", "paravirtual"))));
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

    // mock autoscaling response
    when(mockAwsClientProvider.getAutoScaling(any(NetflixAmazonCredentials.class), anyString()))
        .thenReturn(mockAutoScaling);
    when(mockAwsClientProvider.getAutoScaling(
            any(NetflixAmazonCredentials.class), anyString(), anyBoolean()))
        .thenReturn(mockAutoScaling);

    when(mockAutoScaling.describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class)))
        .thenReturn(new DescribeAutoScalingGroupsResult());
    when(mockAutoScaling.describeLaunchConfigurations(
            any(DescribeLaunchConfigurationsRequest.class)))
        .thenReturn(new DescribeLaunchConfigurationsResult());
  }

  @DisplayName("Assert AWS is enabled and launch template features are disabled")
  @Test
  public void configTest() {
    assertTrue(AWS_ENABLED);
    assertEquals("aws-account1", AWS_ACCOUNT_NAME);
    assertNull(AWS_LAUNCH_TEMPLATES); // assert that launch template config is absent / disabled
  }

  @DisplayName(
      "Given request with launch configuration and default settings with EC2 on-demand, "
          + "successfully submit createServerGroup operation with requested configuration")
  @Test
  public void createServerGroup_defaultSettings_expect_launchConfiguration()
      throws InterruptedException {
    // given
    Map<String, Object> requestBody =
        TestUtils.loadJson(PATH_PREFIX + "createServerGroup-basic.json")
            .withValue("tags", Map.of("testPurpose", "testing default settings"))
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
            String.format(
                EXPECTED_DEPLOY_WITH_LC_MSG_FMT,
                EXPECTED_SERVER_GROUP_NAME,
                EXPECTED_LAUNCH_CONFIG_NAME)));
    assertTrue(taskHistory.contains(EXPECTED_DEPLOY_SUCCESS_MSG));

    // capture and assert arguments
    ArgumentCaptor<CreateLaunchConfigurationRequest> createLaunchConfigArgs =
        ArgumentCaptor.forClass(CreateLaunchConfigurationRequest.class);
    verify(mockAutoScaling).createLaunchConfiguration(createLaunchConfigArgs.capture());
    CreateLaunchConfigurationRequest createLcReq = createLaunchConfigArgs.getValue();

    assertTrue(createLcReq.getLaunchConfigurationName().contains(EXPECTED_LAUNCH_CONFIG_NAME));
    assertEquals("ami-12345", createLcReq.getImageId());
    assertEquals("c3.large", createLcReq.getInstanceType());
    assertEquals(1, createLcReq.getSecurityGroups().size());
    assertEquals("sg-123", createLcReq.getSecurityGroups().get(0));

    ArgumentCaptor<CreateAutoScalingGroupRequest> createAsgArgs =
        ArgumentCaptor.forClass(CreateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).createAutoScalingGroup(createAsgArgs.capture());
    CreateAutoScalingGroupRequest createAsgReq = createAsgArgs.getValue();

    assertTrue(createAsgReq.getLaunchConfigurationName().contains(EXPECTED_LAUNCH_CONFIG_NAME));
    assertEquals(1, createAsgReq.getTags().size());
    assertEquals("testPurpose", createAsgReq.getTags().get(0).getKey());
    assertEquals("testing default settings", createAsgReq.getTags().get(0).getValue());
  }

  @DisplayName(
      "Given request with launch configuration and default settings with Ec2 Spot, "
          + "successfully submit createServerGroup operation with requested configuration")
  @Test
  public void createServerGroup_lcAndSpot_expect_launchConfiguration() throws InterruptedException {
    // given
    Map<String, Object> requestBody =
        TestUtils.loadJson(PATH_PREFIX + "createServerGroup-basic.json")
            .withValue("spotPrice", "1.5")
            .withValue("securityGroup", new String[] {"myAwsApp"})
            .withValue("setLaunchTemplate", false)
            .withValue("tags", Map.of("testPurpose", "testing default settings for spot"))
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
            String.format(
                EXPECTED_DEPLOY_WITH_LC_MSG_FMT,
                EXPECTED_SERVER_GROUP_NAME,
                EXPECTED_LAUNCH_CONFIG_NAME)));
    assertTrue(taskHistory.contains(EXPECTED_DEPLOY_SUCCESS_MSG));

    // capture and assert arguments
    ArgumentCaptor<CreateLaunchConfigurationRequest> createLaunchConfigArgs =
        ArgumentCaptor.forClass(CreateLaunchConfigurationRequest.class);
    verify(mockAutoScaling).createLaunchConfiguration(createLaunchConfigArgs.capture());
    CreateLaunchConfigurationRequest createLcReq = createLaunchConfigArgs.getValue();

    assertTrue(createLcReq.getLaunchConfigurationName().contains(EXPECTED_LAUNCH_CONFIG_NAME));
    assertEquals("ami-12345", createLcReq.getImageId());
    assertEquals("c3.large", createLcReq.getInstanceType());
    assertEquals("1.5", createLcReq.getSpotPrice());
    assertEquals(1, createLcReq.getSecurityGroups().size());
    assertEquals("sg-123", createLcReq.getSecurityGroups().get(0));

    ArgumentCaptor<CreateAutoScalingGroupRequest> createAsgArgs =
        ArgumentCaptor.forClass(CreateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).createAutoScalingGroup(createAsgArgs.capture());
    CreateAutoScalingGroupRequest createAsgReq = createAsgArgs.getValue();

    assertTrue(createAsgReq.getLaunchConfigurationName().contains(EXPECTED_LAUNCH_CONFIG_NAME));
    assertEquals(1, createAsgReq.getTags().size());
    assertEquals("testPurpose", createAsgReq.getTags().get(0).getKey());
    assertEquals("testing default settings for spot", createAsgReq.getTags().get(0).getValue());
  }
}
