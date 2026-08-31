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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import software.amazon.awssdk.services.autoscaling.AutoScalingClient;
import software.amazon.awssdk.services.autoscaling.model.CreateAutoScalingGroupRequest;
import software.amazon.awssdk.services.autoscaling.model.CreateLaunchConfigurationRequest;
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsResponse;
import software.amazon.awssdk.services.autoscaling.model.DescribeLaunchConfigurationsRequest;
import software.amazon.awssdk.services.autoscaling.model.DescribeLaunchConfigurationsResponse;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeAddressesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeImagesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeImagesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceTypesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceTypesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeKeyPairsResponse;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsResponse;
import software.amazon.awssdk.services.ec2.model.DescribeSubnetsResponse;
import software.amazon.awssdk.services.ec2.model.DescribeVpcClassicLinkResponse;
import software.amazon.awssdk.services.ec2.model.DescribeVpcsResponse;
import software.amazon.awssdk.services.ec2.model.Image;
import software.amazon.awssdk.services.ec2.model.InstanceTypeInfo;
import software.amazon.awssdk.services.ec2.model.ProcessorInfo;
import software.amazon.awssdk.services.ec2.model.SecurityGroup;
import software.amazon.awssdk.services.ec2.model.Subnet;
import software.amazon.awssdk.services.ec2.model.Tag;

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
                        .instanceType("c3.large")
                        .processorInfo(
                            ProcessorInfo.builder()
                                .supportedArchitecturesWithStrings("i386", "x86_64")
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

    // mock autoscaling response
    when(mockAwsClientProvider.getAutoScalingV2(any(NetflixAmazonCredentials.class), anyString()))
        .thenReturn(mockAutoScaling);

    when(mockAutoScaling.describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class)))
        .thenReturn(DescribeAutoScalingGroupsResponse.builder().build());
    when(mockAutoScaling.describeLaunchConfigurations(
            any(DescribeLaunchConfigurationsRequest.class)))
        .thenReturn(DescribeLaunchConfigurationsResponse.builder().build());
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

    assertTrue(createLcReq.launchConfigurationName().contains(EXPECTED_LAUNCH_CONFIG_NAME));
    assertEquals("ami-12345", createLcReq.imageId());
    assertEquals("c3.large", createLcReq.instanceType());
    assertEquals(1, createLcReq.securityGroups().size());
    assertEquals("sg-123", createLcReq.securityGroups().get(0));

    ArgumentCaptor<CreateAutoScalingGroupRequest> createAsgArgs =
        ArgumentCaptor.forClass(CreateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).createAutoScalingGroup(createAsgArgs.capture());
    CreateAutoScalingGroupRequest createAsgReq = createAsgArgs.getValue();

    assertTrue(createAsgReq.launchConfigurationName().contains(EXPECTED_LAUNCH_CONFIG_NAME));
    assertEquals(1, createAsgReq.tags().size());
    assertEquals("testPurpose", createAsgReq.tags().get(0).key());
    assertEquals("testing default settings", createAsgReq.tags().get(0).value());
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

    assertTrue(createLcReq.launchConfigurationName().contains(EXPECTED_LAUNCH_CONFIG_NAME));
    assertEquals("ami-12345", createLcReq.imageId());
    assertEquals("c3.large", createLcReq.instanceType());
    assertEquals("1.5", createLcReq.spotPrice());
    assertEquals(1, createLcReq.securityGroups().size());
    assertEquals("sg-123", createLcReq.securityGroups().get(0));

    ArgumentCaptor<CreateAutoScalingGroupRequest> createAsgArgs =
        ArgumentCaptor.forClass(CreateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).createAutoScalingGroup(createAsgArgs.capture());
    CreateAutoScalingGroupRequest createAsgReq = createAsgArgs.getValue();

    assertTrue(createAsgReq.launchConfigurationName().contains(EXPECTED_LAUNCH_CONFIG_NAME));
    assertEquals(1, createAsgReq.tags().size());
    assertEquals("testPurpose", createAsgReq.tags().get(0).key());
    assertEquals("testing default settings for spot", createAsgReq.tags().get(0).value());
  }
}
