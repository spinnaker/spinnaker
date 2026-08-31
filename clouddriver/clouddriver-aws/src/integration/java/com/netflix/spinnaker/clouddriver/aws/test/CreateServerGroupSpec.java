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
import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.autoscaling.AutoScalingClient;
import software.amazon.awssdk.services.autoscaling.model.AlreadyExistsException;
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup;
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
 * Test class for general test cases related to CreateServerGroup operation. Note: launch template
 * settings are enabled in clouddriver.yml
 */
@ActiveProfiles("launch-templates")
public class CreateServerGroupSpec extends AwsBaseSpec {
  private AutoScalingClient mockAutoScaling = mock(AutoScalingClient.class);
  private Ec2Client mockEc2 = mock(Ec2Client.class);

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

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

  @DisplayName("Given invalid requests, successfully validate with error messages")
  @Test
  public void createServerGroup_invalidRequests_expect_validationFailure() {
    final String invalidReqDir = "/createServerGroup_invalid_requests/";
    final String pattern = PATH_PREFIX + invalidReqDir + "*.json";
    TestUtils.loadResourcesFromDir(pattern).stream()
        .forEach(
            ti -> {
              final String testFileName = ti.getFilename();
              System.out.println("\nRunning tests for " + invalidReqDir + testFileName);

              // given
              Map<String, Object> requestBody = TestUtils.loadJson(ti).asMap();

              // when, then
              final String expectedValidationError =
                  (testFileName.contains("-")
                          ? StringUtils.substringAfterLast(testFileName, "-")
                          : testFileName)
                      .split(".json")[0];

              given()
                  .contentType(ContentType.JSON)
                  .body(requestBody)
                  .when()
                  .post(getBaseUrl() + CREATE_SERVER_GROUP_OP_PATH)
                  .then()
                  .statusCode(400)
                  .contentType(ContentType.JSON)
                  .assertThat()
                  .body("message", Matchers.equalTo("Validation Failed"))
                  .body("errors.size()", Matchers.equalTo(1))
                  .body("errors[0]", Matchers.endsWith(expectedValidationError));
            });
  }

  @DisplayName("Given request with subnet type, successfully submit deployment to subnet IDs")
  @Test
  public void createServerGroup_subnetType_expect_deploymentToSubnetIds()
      throws InterruptedException {
    // given
    Map<String, Object> requestBody =
        TestUtils.loadJson(PATH_PREFIX + "createServerGroup-basic.json")
            .withValue("setLaunchTemplate", false)
            .withValue("subnetType", "internal")
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
    assertTrue(taskHistory.contains(EXPECTED_DEPLOY_SUCCESS_MSG));
    assertTrue(taskHistory.contains("Deploying to subnetIds: subnetId1"));

    // capture and assert arguments
    ArgumentCaptor<CreateAutoScalingGroupRequest> createAsgArgs =
        ArgumentCaptor.forClass(CreateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).createAutoScalingGroup(createAsgArgs.capture());
    CreateAutoScalingGroupRequest createAsgReq = createAsgArgs.getValue();

    assertEquals("subnetId1", createAsgReq.vpcZoneIdentifier());
    assertTrue(createAsgReq.availabilityZones().isEmpty());
  }

  @DisplayName("Given request with invalid subnet type, fail with accurate message")
  @Test
  public void createServerGroup_invalid_subnetType_expect_error() throws InterruptedException {
    // given
    Map<String, Object> requestBody =
        TestUtils.loadJson(PATH_PREFIX + "createServerGroup-basic.json")
            .withValue("subnetType", "unknown")
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
            "Orchestration failed: DeployAtomicOperation | RuntimeException: [No suitable subnet was found for internal subnet purpose 'unknown'!]"));
  }

  @DisplayName(
      "Given request without subnet type, successfully submit deployment to availability zones")
  @Test
  public void createServerGroup_noSubnetType_expect_deploymentToAZs() throws InterruptedException {
    // given
    Map<String, Object> requestBody =
        TestUtils.loadJson(PATH_PREFIX + "createServerGroup-basic.json").asMap();

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
    assertTrue(taskHistory.contains(EXPECTED_DEPLOY_SUCCESS_MSG));
    assertTrue(taskHistory.contains("Deploying to availabilityZones: [us-west-1a, us-west-1c]"));

    // capture and assert arguments
    ArgumentCaptor<CreateAutoScalingGroupRequest> createAsgArgs =
        ArgumentCaptor.forClass(CreateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).createAutoScalingGroup(createAsgArgs.capture());
    CreateAutoScalingGroupRequest createAsgReq = createAsgArgs.getValue();

    assertEquals(Arrays.asList("us-west-1a", "us-west-1c"), createAsgReq.availabilityZones());
    assertNull(createAsgReq.vpcZoneIdentifier());
  }

  @DisplayName(
      "Given request to create server group that already exists "
          + "and creation time not in safety window, fail with accurate message")
  @Test
  public void createServerGroup_alreadyExists_notInSafetyWindow_expect_exception()
      throws InterruptedException {
    // given
    final String expectedServerGroupName = "myAwsApp-myStack-v100";
    Map<String, Object> requestBody =
        TestUtils.loadJson(PATH_PREFIX + "createServerGroup-basic.json")
            .withValue("sequence", "100")
            .withValue("setLaunchTemplate", true)
            .asMap();

    // when - create myAwsApp-myStack-v100 first and verify
    String taskId1 =
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
    String taskHistory1 = getTaskUpdatesAfterCompletion(taskId1);
    assertTrue(taskHistory1.contains(EXPECTED_DEPLOY_SUCCESS_MSG));

    // when
    final Instant notWithinOneHour = Instant.now().minus(2, ChronoUnit.HOURS);
    when(mockAutoScaling.createAutoScalingGroup(any(CreateAutoScalingGroupRequest.class)))
        .thenThrow(AlreadyExistsException.builder().build());
    when(mockAutoScaling.describeAutoScalingGroups(
            DescribeAutoScalingGroupsRequest.builder()
                .autoScalingGroupNames(expectedServerGroupName)
                .build()))
        .thenReturn(
            DescribeAutoScalingGroupsResponse.builder()
                .autoScalingGroups(
                    Arrays.asList(
                        AutoScalingGroup.builder()
                            .autoScalingGroupName(expectedServerGroupName)
                            .healthCheckType("EC2")
                            .launchTemplate(
                                LaunchTemplateSpecification.builder()
                                    .launchTemplateId("lt-1")
                                    .version("1")
                                    .build())
                            .availabilityZones(Arrays.asList("us-west-1a", "us-west-1c"))
                            .createdTime(notWithinOneHour)
                            .build()))
                .build());

    // then, try to create myAwsApp-myStack-v100 again
    String taskId2 =
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
    String taskHistory2 = getTaskUpdatesAfterCompletion(taskId2);
    assertThat(taskHistory2)
        .contains(
            expectedServerGroupName
                + " already exists and appears to be valid, but falls outside of safety window for idempotent deploy (1 hour)");
    assertThat(taskHistory2)
        .contains("Orchestration failed: DeployAtomicOperation | AlreadyExistsException");
  }

  @DisplayName(
      "Given request to create server group that already exists "
          + "and creation time in safety window, fail with accurate message")
  @Test
  public void createServerGroup_alreadyExists_inSafetyWindow_expect_success()
      throws InterruptedException {
    // given
    final String expectedServerGroupName = "myAwsApp-myStack-v200";
    Map<String, Object> requestBody =
        TestUtils.loadJson(PATH_PREFIX + "createServerGroup-basic.json")
            .withValue("sequence", "200")
            .withValue("setLaunchTemplate", true)
            .asMap();

    // when - create myAwsApp-myStack-v200 first and verify
    String taskId1 =
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
    String taskHistory1 = getTaskUpdatesAfterCompletion(taskId1);
    assertTrue(taskHistory1.contains(EXPECTED_DEPLOY_SUCCESS_MSG));

    // when
    final Instant withinOneHour = Instant.now().minus(2, ChronoUnit.MINUTES);
    when(mockAutoScaling.createAutoScalingGroup(any(CreateAutoScalingGroupRequest.class)))
        .thenThrow(AlreadyExistsException.builder().build());
    when(mockAutoScaling.describeAutoScalingGroups(
            DescribeAutoScalingGroupsRequest.builder()
                .autoScalingGroupNames(expectedServerGroupName)
                .build()))
        .thenReturn(
            DescribeAutoScalingGroupsResponse.builder()
                .autoScalingGroups(
                    Arrays.asList(
                        AutoScalingGroup.builder()
                            .autoScalingGroupName(expectedServerGroupName)
                            .healthCheckType("EC2")
                            .launchTemplate(
                                LaunchTemplateSpecification.builder()
                                    .launchTemplateId("lt-1")
                                    .version("1")
                                    .build())
                            .availabilityZones(Arrays.asList("us-west-1a", "us-west-1c"))
                            .createdTime(withinOneHour)
                            .build()))
                .build());

    // then, try to create myAwsApp-myStack-v200 again
    String taskId2 =
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
    String taskHistory2 = getTaskUpdatesAfterCompletion(taskId2);
    assertThat(taskHistory2).contains(EXPECTED_DEPLOY_SUCCESS_MSG);
  }

  @DisplayName(
      "Given request to create server group with monitoring enabled, "
          + "successfully submit create server group operation")
  @Test
  public void createServerGroup_metrics_monitoring_enabled_expect_success()
      throws InterruptedException {
    // given
    Map<String, Object> requestBody =
        TestUtils.loadJson(PATH_PREFIX + "createServerGroup-basic.json")
            .withValue("instanceMonitoring", true)
            .withValue("enabledMetrics", new String[] {"GroupMinSize", "GroupMaxSize"})
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
    assertTrue(taskHistory.contains("Enabling metrics collection for:"));
  }
}
