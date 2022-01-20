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
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateRequest;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateResult;
import com.amazonaws.services.ec2.model.DescribeAddressesResult;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.DescribeInstanceTypesRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceTypesResult;
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
import com.amazonaws.services.ec2.model.InstanceTypeInfo;
import com.amazonaws.services.ec2.model.LaunchTemplate;
import com.amazonaws.services.ec2.model.ProcessorInfo;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.VirtualizationType;
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
import org.springframework.test.context.ActiveProfiles;

/**
 * Test class with launch template settings enabled in clouddriver.yml, for CreateServerGroup
 * operation.
 */
@ActiveProfiles("launch-templates")
public class CreateServerGroupLaunchTemplatesEnabledSpec extends AwsBaseSpec {
  @Value("${aws.features.launch-templates.enabled}")
  private Boolean AWS_LAUNCH_TEMPLATES_ENABLED;

  @Value("${aws.features.launch-templates.allowed-applications}")
  private String AWS_LAUNCH_TEMPLATES_ALLOWED_APPS;

  @Value("${aws.features.launch-templates.excluded-applications}")
  private String AWS_LAUNCH_TEMPLATES_EXCLUDED_APPS;

  private final String EXPECTED_SERVER_GROUP_NAME = "myAwsApp-myStack-v000";
  private final String EXPECTED_LAUNCH_TEMPLATE_ID = "lt-1";
  private final LaunchTemplateSpecification EXPECTED_LAUNCH_TEMPLATE_SPEC =
      new LaunchTemplateSpecification().withLaunchTemplateId("lt-1").withVersion("1");
  private final String EXPECTED_DEPLOY_WITH_LT_MSG_FMT = "Deploying ASG %s with launch template %s";

  private final String EXPECTED_DEPLOY_WITH_LC_MSG_FMT =
      "Deploying ASG %s with launch configuration %s";
  private final String EXPECTED_LAUNCH_CONFIG_NAME =
      "myAwsApp-myStack-v000-"; // partial name without the timestamp part

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
                        .withInstanceType("t3.medium")
                        .withProcessorInfo(new ProcessorInfo().withSupportedArchitectures("x86_64"))
                        .withSupportedVirtualizationTypes(Arrays.asList("hvm")),
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

    assertTrue(AWS_LAUNCH_TEMPLATES_ENABLED);
    assertEquals("myAwsApp:aws-account1:us-west-1", AWS_LAUNCH_TEMPLATES_ALLOWED_APPS);
    assertEquals("myExcludedApp:aws-account1:us-west-1", AWS_LAUNCH_TEMPLATES_EXCLUDED_APPS);
  }

  @DisplayName(
      "Given request for server group with launch template features, "
          + "successfully submit createServerGroup operation with launch template")
  @Test
  public void createServerGroup_ltFeatures_used_expect_launchTemplate()
      throws InterruptedException {
    // given
    Map<String, Object> requestBody =
        TestUtils.loadJson(PATH_PREFIX + "createServerGroup-basic.json")
            .withValue("setLaunchTemplate", true)
            .withValue("requireIMDSv2", true)
            .withValue("associateIPv6Address", true)
            .withValue("unlimitedCpuCredits", true)
            .withValue("instanceType", "t3.medium")
            .withValue("securityGroup", new String[] {"myAwsApp"})
            .withValue(
                "tags", Map.of("testPurpose", "testing server group with launch template features"))
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
                EXPECTED_DEPLOY_WITH_LT_MSG_FMT,
                EXPECTED_SERVER_GROUP_NAME,
                EXPECTED_LAUNCH_TEMPLATE_ID)));
    assertTrue(taskHistory.contains(EXPECTED_DEPLOY_SUCCESS_MSG));

    // capture and assert arguments
    ArgumentCaptor<CreateLaunchTemplateRequest> createLaunchTemplateArgs =
        ArgumentCaptor.forClass(CreateLaunchTemplateRequest.class);
    verify(mockEc2).createLaunchTemplate(createLaunchTemplateArgs.capture());
    CreateLaunchTemplateRequest createLtReq = createLaunchTemplateArgs.getValue();

    assertTrue(createLtReq.getLaunchTemplateName().contains("myAwsApp-myStack-v000-"));
    assertEquals("ami-12345", createLtReq.getLaunchTemplateData().getImageId());
    assertEquals("t3.medium", createLtReq.getLaunchTemplateData().getInstanceType());
    assertEquals(
        1, createLtReq.getLaunchTemplateData().getNetworkInterfaces().get(0).getGroups().size());
    assertEquals(
        "sg-123",
        createLtReq.getLaunchTemplateData().getNetworkInterfaces().get(0).getGroups().get(0));

    assertEquals(
        "unlimited", createLtReq.getLaunchTemplateData().getCreditSpecification().getCpuCredits());
    assertEquals(
        "required", createLtReq.getLaunchTemplateData().getMetadataOptions().getHttpTokens());
    assertEquals(
        1, createLtReq.getLaunchTemplateData().getNetworkInterfaces().get(0).getIpv6AddressCount());

    assertNull(createLtReq.getLaunchTemplateData().getInstanceMarketOptions());
    assertNull(createLtReq.getLaunchTemplateData().getPlacement());
    assertTrue(createLtReq.getLaunchTemplateData().getLicenseSpecifications().isEmpty());

    ArgumentCaptor<CreateAutoScalingGroupRequest> createAsgArgs =
        ArgumentCaptor.forClass(CreateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).createAutoScalingGroup(createAsgArgs.capture());
    CreateAutoScalingGroupRequest createAsgReq = createAsgArgs.getValue();

    assertEquals(EXPECTED_LAUNCH_TEMPLATE_SPEC, createAsgReq.getLaunchTemplate());
    assertEquals(1, createAsgReq.getTags().size());
    assertEquals("testPurpose", createAsgReq.getTags().get(0).getKey());
    assertEquals(
        "testing server group with launch template features",
        createAsgReq.getTags().get(0).getValue());
  }

  @DisplayName(
      "Given request for server group with launch template, and EC2 Spot, "
          + "successfully submit createServerGroup operation with launch template")
  @Test
  public void createServerGroup_ltAndSpot_expect_launchTemplate() throws InterruptedException {
    // given
    Map<String, Object> requestBody =
        TestUtils.loadJson(PATH_PREFIX + "createServerGroup-basic.json")
            .withValue("setLaunchTemplate", true)
            .withValue("spotPrice", "1.5")
            .withValue("securityGroup", new String[] {"myAwsApp"})
            .withValue("instanceType", "t3.medium")
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
                EXPECTED_DEPLOY_WITH_LT_MSG_FMT,
                EXPECTED_SERVER_GROUP_NAME,
                EXPECTED_LAUNCH_TEMPLATE_ID)));
    assertTrue(taskHistory.contains(EXPECTED_DEPLOY_SUCCESS_MSG));

    // capture and assert arguments
    ArgumentCaptor<CreateLaunchTemplateRequest> createLaunchTemplateArgs =
        ArgumentCaptor.forClass(CreateLaunchTemplateRequest.class);
    verify(mockEc2).createLaunchTemplate(createLaunchTemplateArgs.capture());
    CreateLaunchTemplateRequest createLtReq = createLaunchTemplateArgs.getValue();

    assertTrue(createLtReq.getLaunchTemplateName().contains("myAwsApp-myStack-v000-"));
    assertEquals("ami-12345", createLtReq.getLaunchTemplateData().getImageId());
    assertEquals("t3.medium", createLtReq.getLaunchTemplateData().getInstanceType());
    assertEquals(
        "spot", createLtReq.getLaunchTemplateData().getInstanceMarketOptions().getMarketType());
    assertEquals(
        "1.5",
        createLtReq
            .getLaunchTemplateData()
            .getInstanceMarketOptions()
            .getSpotOptions()
            .getMaxPrice());
    assertEquals(
        "standard",
        createLtReq
            .getLaunchTemplateData()
            .getCreditSpecification()
            .getCpuCredits()); // default for t3 type

    ArgumentCaptor<CreateAutoScalingGroupRequest> createAsgArgs =
        ArgumentCaptor.forClass(CreateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).createAutoScalingGroup(createAsgArgs.capture());
    CreateAutoScalingGroupRequest createAsgReq = createAsgArgs.getValue();

    assertEquals(EXPECTED_LAUNCH_TEMPLATE_SPEC, createAsgReq.getLaunchTemplate());
  }

  @DisplayName("Given request with incompatible AMI and instance type, fail with accurate message")
  @Test
  public void createServerGroup_incompatible_ami_and_instanceType_expect_exception()
      throws InterruptedException {
    // given
    Map<String, Object> requestBody =
        TestUtils.loadJson(PATH_PREFIX + "createServerGroup-basic.json")
            .withValue("setLaunchTemplate", true)
            .withValue("spotPrice", "1.5")
            .withValue("securityGroup", new String[] {"myAwsApp"})
            .withValue("instanceType", "t3.medium")
            .asMap();
    when(mockEc2.describeImages(any(DescribeImagesRequest.class)))
        .thenReturn(
            new DescribeImagesResult()
                .withImages(
                    new Image()
                        .withImageId("img-1")
                        .withName("test-image")
                        .withVirtualizationType(VirtualizationType.Paravirtual)));

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
            "Orchestration failed: DeployAtomicOperation | IllegalArgumentException: [Instance type t3.medium does not support virtualization type paravirtual. Please select a different image or instance type.]"));
  }

  @DisplayName(
      "Given request with setLaunchTemplate disabled, "
          + "successfully submit createServerGroup operation with launch configuration")
  @Test
  public void createServerGroup_setLaunchTemplateDisabled_expect_launchConfiguration()
      throws InterruptedException {
    // given
    Map<String, Object> requestBody =
        TestUtils.loadJson(PATH_PREFIX + "createServerGroup-basic.json")
            .withValue("setLaunchTemplate", false)
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
    assertTrue(
        taskHistory.contains(
            String.format(EXPECTED_SERVER_GROUP_NAME, EXPECTED_SERVER_GROUP_NAME)));

    // capture and assert arguments
    ArgumentCaptor<CreateLaunchConfigurationRequest> createLaunchConfigArgs =
        ArgumentCaptor.forClass(CreateLaunchConfigurationRequest.class);
    verify(mockAutoScaling).createLaunchConfiguration(createLaunchConfigArgs.capture());
    CreateLaunchConfigurationRequest createLcReq = createLaunchConfigArgs.getValue();

    assertTrue(createLcReq.getLaunchConfigurationName().contains(EXPECTED_LAUNCH_CONFIG_NAME));
    assertEquals(1, createLcReq.getSecurityGroups().size());
    assertEquals("sg-123", createLcReq.getSecurityGroups().get(0));
    assertEquals("ami-12345", createLcReq.getImageId());
    assertEquals("c3.large", createLcReq.getInstanceType());

    ArgumentCaptor<CreateAutoScalingGroupRequest> createAsgArgs =
        ArgumentCaptor.forClass(CreateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).createAutoScalingGroup(createAsgArgs.capture());
    CreateAutoScalingGroupRequest createAsgReq = createAsgArgs.getValue();

    assertTrue(createAsgReq.getLaunchConfigurationName().contains(EXPECTED_LAUNCH_CONFIG_NAME));
  }

  @DisplayName(
      "Given request with setLaunchTemplate enabled for an excluded application, "
          + "successfully submit createServerGroup operation with launch configuration")
  @Test
  public void createServerGroup_ltEnabled_and_excludedApp_expect_launchConfiguration()
      throws InterruptedException {
    // given
    Map<String, Object> requestBody =
        TestUtils.loadJson(PATH_PREFIX + "createServerGroup-basic.json")
            .withValue("application", "myExcludedApp")
            .withValue("instanceType", "c3.large")
            .withValue("setLaunchTemplate", false)
            .asMap();
    when(mockEc2.describeSecurityGroups(any(DescribeSecurityGroupsRequest.class)))
        .thenReturn(
            new DescribeSecurityGroupsResult()
                .withSecurityGroups(
                    new SecurityGroup().withGroupId("sg-123").withGroupName("myExcludedApp")));

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
    final String expectedSgName = "myExcludedApp-myStack-v000";
    final String expectedLcName =
        expectedSgName + "-"; // partial launch config name without the timestamp
    final String taskHistory = getTaskUpdatesAfterCompletion(taskId);
    assertTrue(
        taskHistory.contains(
            String.format(EXPECTED_DEPLOY_WITH_LC_MSG_FMT, expectedSgName, expectedLcName)));
    assertTrue(taskHistory.contains(EXPECTED_DEPLOY_SUCCESS_MSG));

    // capture and assert arguments
    ArgumentCaptor<CreateLaunchConfigurationRequest> createLaunchConfigArgs =
        ArgumentCaptor.forClass(CreateLaunchConfigurationRequest.class);
    verify(mockAutoScaling).createLaunchConfiguration(createLaunchConfigArgs.capture());
    CreateLaunchConfigurationRequest createLcReq = createLaunchConfigArgs.getValue();

    assertTrue(createLcReq.getLaunchConfigurationName().contains(expectedLcName));
    assertEquals(1, createLcReq.getSecurityGroups().size());
    assertEquals("sg-123", createLcReq.getSecurityGroups().get(0));
    assertEquals("ami-12345", createLcReq.getImageId());
    assertEquals("c3.large", createLcReq.getInstanceType());

    ArgumentCaptor<CreateAutoScalingGroupRequest> createAsgArgs =
        ArgumentCaptor.forClass(CreateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).createAutoScalingGroup(createAsgArgs.capture());
    CreateAutoScalingGroupRequest createAsgReq = createAsgArgs.getValue();

    assertTrue(createAsgReq.getLaunchConfigurationName().contains(expectedLcName));
  }
}
