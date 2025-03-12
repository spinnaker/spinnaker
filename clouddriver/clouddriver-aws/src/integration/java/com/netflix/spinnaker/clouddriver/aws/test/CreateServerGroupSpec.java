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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.model.AlreadyExistsException;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
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
import com.amazonaws.services.ec2.model.LaunchTemplate;
import com.amazonaws.services.ec2.model.ProcessorInfo;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Tag;
import com.netflix.spinnaker.clouddriver.aws.AwsBaseSpec;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.utils.TestUtils;
import io.restassured.http.ContentType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test class for general test cases related to CreateServerGroup operation. Note: launch template
 * settings are enabled in clouddriver.yml
 */
@ActiveProfiles("launch-templates")
public class CreateServerGroupSpec extends AwsBaseSpec {
  private AmazonAutoScaling mockAutoScaling = mock(AmazonAutoScaling.class);
  private AmazonEC2 mockEc2 = mock(AmazonEC2.class);

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

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

    assertEquals("subnetId1", createAsgReq.getVPCZoneIdentifier());
    assertTrue(createAsgReq.getAvailabilityZones().isEmpty());
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

    assertEquals(Arrays.asList("us-west-1a", "us-west-1c"), createAsgReq.getAvailabilityZones());
    assertNull(createAsgReq.getVPCZoneIdentifier());
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
    final Date notWithinOneHour = Date.from(Instant.now().minus(2, ChronoUnit.HOURS));
    when(mockAutoScaling.createAutoScalingGroup(any(CreateAutoScalingGroupRequest.class)))
        .thenThrow(AlreadyExistsException.class);
    when(mockAutoScaling.describeAutoScalingGroups(
            new DescribeAutoScalingGroupsRequest()
                .withAutoScalingGroupNames(expectedServerGroupName)))
        .thenReturn(
            new DescribeAutoScalingGroupsResult()
                .withAutoScalingGroups(
                    Arrays.asList(
                        new AutoScalingGroup()
                            .withAutoScalingGroupName(expectedServerGroupName)
                            .withHealthCheckType("EC2")
                            .withLaunchTemplate(
                                new LaunchTemplateSpecification()
                                    .withLaunchTemplateId("lt-1")
                                    .withVersion("1"))
                            .withAvailabilityZones(Arrays.asList("us-west-1a", "us-west-1c"))
                            .withCreatedTime(notWithinOneHour))));

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
    final Date withinOneHour = Date.from(Instant.now().minus(2, ChronoUnit.MINUTES));
    when(mockAutoScaling.createAutoScalingGroup(any(CreateAutoScalingGroupRequest.class)))
        .thenThrow(AlreadyExistsException.class);
    when(mockAutoScaling.describeAutoScalingGroups(
            new DescribeAutoScalingGroupsRequest()
                .withAutoScalingGroupNames(expectedServerGroupName)))
        .thenReturn(
            new DescribeAutoScalingGroupsResult()
                .withAutoScalingGroups(
                    Arrays.asList(
                        new AutoScalingGroup()
                            .withAutoScalingGroupName(expectedServerGroupName)
                            .withHealthCheckType("EC2")
                            .withLaunchTemplate(
                                new LaunchTemplateSpecification()
                                    .withLaunchTemplateId("lt-1")
                                    .withVersion("1"))
                            .withAvailabilityZones(Arrays.asList("us-west-1a", "us-west-1c"))
                            .withCreatedTime(withinOneHour))));

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
