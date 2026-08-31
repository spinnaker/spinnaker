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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.autoscaling.AutoScalingClient;
import software.amazon.awssdk.services.autoscaling.model.CreateAutoScalingGroupRequest;
import software.amazon.awssdk.services.autoscaling.model.CreateLaunchConfigurationRequest;
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
import software.amazon.awssdk.services.ec2.model.VirtualizationType;

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
      LaunchTemplateSpecification.builder().launchTemplateId("lt-1").version("1").build();
  private final String EXPECTED_DEPLOY_WITH_LT_MSG_FMT = "Deploying ASG %s with launch template %s";

  private final String EXPECTED_DEPLOY_WITH_LC_MSG_FMT =
      "Deploying ASG %s with launch configuration %s";
  private final String EXPECTED_LAUNCH_CONFIG_NAME =
      "myAwsApp-myStack-v000-"; // partial name without the timestamp part

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

    assertTrue(createLtReq.launchTemplateName().contains("myAwsApp-myStack-v000-"));
    assertEquals("ami-12345", createLtReq.launchTemplateData().imageId());
    assertEquals("t3.medium", createLtReq.launchTemplateData().instanceTypeAsString());
    assertEquals(1, createLtReq.launchTemplateData().networkInterfaces().get(0).groups().size());
    assertEquals(
        "sg-123", createLtReq.launchTemplateData().networkInterfaces().get(0).groups().get(0));

    assertEquals("unlimited", createLtReq.launchTemplateData().creditSpecification().cpuCredits());
    assertEquals(
        "required", createLtReq.launchTemplateData().metadataOptions().httpTokensAsString());
    assertEquals(1, createLtReq.launchTemplateData().networkInterfaces().get(0).ipv6AddressCount());

    assertNull(createLtReq.launchTemplateData().instanceMarketOptions());
    assertNull(createLtReq.launchTemplateData().placement());
    assertTrue(createLtReq.launchTemplateData().licenseSpecifications().isEmpty());

    ArgumentCaptor<CreateAutoScalingGroupRequest> createAsgArgs =
        ArgumentCaptor.forClass(CreateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).createAutoScalingGroup(createAsgArgs.capture());
    CreateAutoScalingGroupRequest createAsgReq = createAsgArgs.getValue();

    assertEquals(EXPECTED_LAUNCH_TEMPLATE_SPEC, createAsgReq.launchTemplate());
    assertEquals(1, createAsgReq.tags().size());
    assertEquals("testPurpose", createAsgReq.tags().get(0).key());
    assertEquals(
        "testing server group with launch template features", createAsgReq.tags().get(0).value());
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

    assertTrue(createLtReq.launchTemplateName().contains("myAwsApp-myStack-v000-"));
    assertEquals("ami-12345", createLtReq.launchTemplateData().imageId());
    assertEquals("t3.medium", createLtReq.launchTemplateData().instanceTypeAsString());
    assertEquals(
        "spot", createLtReq.launchTemplateData().instanceMarketOptions().marketTypeAsString());
    assertEquals(
        "1.5", createLtReq.launchTemplateData().instanceMarketOptions().spotOptions().maxPrice());
    assertEquals(
        "standard",
        createLtReq.launchTemplateData().creditSpecification().cpuCredits()); // default for t3 type

    ArgumentCaptor<CreateAutoScalingGroupRequest> createAsgArgs =
        ArgumentCaptor.forClass(CreateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).createAutoScalingGroup(createAsgArgs.capture());
    CreateAutoScalingGroupRequest createAsgReq = createAsgArgs.getValue();

    assertEquals(EXPECTED_LAUNCH_TEMPLATE_SPEC, createAsgReq.launchTemplate());
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
            DescribeImagesResponse.builder()
                .images(
                    Image.builder()
                        .imageId("img-1")
                        .name("test-image")
                        .virtualizationType(VirtualizationType.PARAVIRTUAL)
                        .build())
                .build());

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

    assertTrue(createLcReq.launchConfigurationName().contains(EXPECTED_LAUNCH_CONFIG_NAME));
    assertEquals(1, createLcReq.securityGroups().size());
    assertEquals("sg-123", createLcReq.securityGroups().get(0));
    assertEquals("ami-12345", createLcReq.imageId());
    assertEquals("c3.large", createLcReq.instanceType());

    ArgumentCaptor<CreateAutoScalingGroupRequest> createAsgArgs =
        ArgumentCaptor.forClass(CreateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).createAutoScalingGroup(createAsgArgs.capture());
    CreateAutoScalingGroupRequest createAsgReq = createAsgArgs.getValue();

    assertTrue(createAsgReq.launchConfigurationName().contains(EXPECTED_LAUNCH_CONFIG_NAME));
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
            DescribeSecurityGroupsResponse.builder()
                .securityGroups(
                    SecurityGroup.builder().groupId("sg-123").groupName("myExcludedApp").build())
                .build());

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

    assertTrue(createLcReq.launchConfigurationName().contains(expectedLcName));
    assertEquals(1, createLcReq.securityGroups().size());
    assertEquals("sg-123", createLcReq.securityGroups().get(0));
    assertEquals("ami-12345", createLcReq.imageId());
    assertEquals("c3.large", createLcReq.instanceType());

    ArgumentCaptor<CreateAutoScalingGroupRequest> createAsgArgs =
        ArgumentCaptor.forClass(CreateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).createAutoScalingGroup(createAsgArgs.capture());
    CreateAutoScalingGroupRequest createAsgReq = createAsgArgs.getValue();

    assertTrue(createAsgReq.launchConfigurationName().contains(expectedLcName));
  }
}
