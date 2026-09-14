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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.aws.AwsBaseSpec;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.services.AsgService;
import com.netflix.spinnaker.clouddriver.aws.utils.TestUtils;
import io.restassured.http.ContentType;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import retrofit2.mock.Calls;
import software.amazon.awssdk.services.autoscaling.AutoScalingClient;
import software.amazon.awssdk.services.autoscaling.model.AutoScalingException;
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup;
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsResponse;
import software.amazon.awssdk.services.autoscaling.model.InstancesDistribution;
import software.amazon.awssdk.services.autoscaling.model.LaunchTemplateOverrides;
import software.amazon.awssdk.services.autoscaling.model.LaunchTemplateSpecification;
import software.amazon.awssdk.services.autoscaling.model.MixedInstancesPolicy;
import software.amazon.awssdk.services.autoscaling.model.UpdateAutoScalingGroupRequest;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.CreateLaunchTemplateVersionRequest;
import software.amazon.awssdk.services.ec2.model.CreateLaunchTemplateVersionResponse;
import software.amazon.awssdk.services.ec2.model.CreditSpecification;
import software.amazon.awssdk.services.ec2.model.DeleteLaunchTemplateVersionsRequest;
import software.amazon.awssdk.services.ec2.model.DeleteLaunchTemplateVersionsResponse;
import software.amazon.awssdk.services.ec2.model.DeleteLaunchTemplateVersionsResponseErrorItem;
import software.amazon.awssdk.services.ec2.model.DeleteLaunchTemplateVersionsResponseSuccessItem;
import software.amazon.awssdk.services.ec2.model.DescribeImagesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeImagesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeLaunchTemplateVersionsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeLaunchTemplateVersionsResponse;
import software.amazon.awssdk.services.ec2.model.Image;
import software.amazon.awssdk.services.ec2.model.LaunchTemplateInstanceMarketOptions;
import software.amazon.awssdk.services.ec2.model.LaunchTemplateSpotMarketOptions;
import software.amazon.awssdk.services.ec2.model.LaunchTemplateVersion;
import software.amazon.awssdk.services.ec2.model.ResponseError;
import software.amazon.awssdk.services.ec2.model.ResponseLaunchTemplateData;

/**
 * Test class for general test cases related to CreateServerGroup operation. Note: launch template
 * settings are enabled in clouddriver.yml
 */
@ActiveProfiles("launch-templates")
public class ModifyServerGroupLaunchTemplateSpec extends AwsBaseSpec {
  @Autowired ApplicationContext context;

  private AsgService mockAsgService = mock(AsgService.class);
  private Ec2Client mockEc2 = mock(Ec2Client.class);
  private AutoScalingClient mockAutoScaling = mock(AutoScalingClient.class);

  private static final String ASG_NAME = "myasg";

  // ASG with Launch Template
  private final LaunchTemplateVersion ltVersionOld =
      LaunchTemplateVersion.builder()
          .launchTemplateId("lt-1")
          .launchTemplateName("lt-1")
          .versionNumber(1L)
          .launchTemplateData(
              ResponseLaunchTemplateData.builder()
                  .imageId("ami-12345")
                  .instanceType("t3.large")
                  .build())
          .build();

  private final LaunchTemplateVersion ltVersionNew =
      LaunchTemplateVersion.builder()
          .launchTemplateId("lt-1")
          .launchTemplateName("lt-1")
          .versionNumber(2L)
          .launchTemplateData(
              ResponseLaunchTemplateData.builder()
                  .imageId("ami-12345")
                  .instanceType("t3.large")
                  .build())
          .build();

  private final AutoScalingGroup asgWithLt =
      AutoScalingGroup.builder()
          .autoScalingGroupName(ASG_NAME)
          .launchTemplate(
              LaunchTemplateSpecification.builder()
                  .launchTemplateId(ltVersionOld.launchTemplateId())
                  .version(String.valueOf(ltVersionOld.versionNumber()))
                  .build())
          .build();

  // ASG with Mixed Instances Policy
  BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType override1 =
      new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType.Builder()
          .instanceType("some.type.large")
          .weightedCapacity("2")
          .build();
  BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType override2 =
      new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType.Builder()
          .instanceType("some.type.xlarge")
          .weightedCapacity("4")
          .build();
  List<LaunchTemplateOverrides> ltOverrides =
      Arrays.asList(
          LaunchTemplateOverrides.builder()
              .instanceType(override1.getInstanceType())
              .weightedCapacity(override1.getWeightedCapacity())
              .build(),
          LaunchTemplateOverrides.builder()
              .instanceType(override2.getInstanceType())
              .weightedCapacity(override2.getWeightedCapacity())
              .build());
  InstancesDistribution instancesDist =
      InstancesDistribution.builder()
          .onDemandBaseCapacity(1)
          .onDemandPercentageAboveBaseCapacity(50)
          .spotInstancePools(5)
          .spotAllocationStrategy("lowest-price")
          .spotMaxPrice("1.5")
          .build();
  private final AutoScalingGroup asgWithMip =
      AutoScalingGroup.builder()
          .autoScalingGroupName(ASG_NAME)
          .mixedInstancesPolicy(
              MixedInstancesPolicy.builder()
                  .launchTemplate(
                      software.amazon.awssdk.services.autoscaling.model.LaunchTemplate.builder()
                          .overrides(ltOverrides)
                          .launchTemplateSpecification(
                              LaunchTemplateSpecification.builder()
                                  .launchTemplateId(ltVersionOld.launchTemplateId())
                                  .version("$Latest")
                                  .build())
                          .build())
                  .instancesDistribution(instancesDist)
                  .build())
          .build();

  @BeforeEach
  public void setup() {

    // mock autoscaling responses
    when(mockAwsClientProvider.getAutoScalingV2(any(NetflixAmazonCredentials.class), anyString()))
        .thenReturn(mockAutoScaling);
    when(mockRegionScopedProvider.getAsgService()).thenReturn(mockAsgService);

    // mock Front50 service responses
    Map applicationMap = new HashMap();
    applicationMap.put("application", "myAwsApp");
    applicationMap.put("legacyUdf", null);
    when(mockFront50Service.getApplication(ASG_NAME)).thenReturn(Calls.response(applicationMap));

    // mock EC2 responses
    when(mockRegionScopedProvider.getAmazonEC2()).thenReturn(mockEc2);
    when(mockAwsClientProvider.getAmazonEC2V2(any(NetflixAmazonCredentials.class), anyString()))
        .thenReturn(mockEc2);
    when(mockEc2.describeLaunchTemplateVersions(any(DescribeLaunchTemplateVersionsRequest.class)))
        .thenReturn(
            DescribeLaunchTemplateVersionsResponse.builder()
                .launchTemplateVersions(ltVersionOld)
                .build());
    when(mockEc2.describeImages(any(DescribeImagesRequest.class)))
        .thenReturn(
            DescribeImagesResponse.builder()
                .images(Image.builder().imageId("ami-12345").build())
                .build());
    when(mockEc2.createLaunchTemplateVersion(any(CreateLaunchTemplateVersionRequest.class)))
        .thenReturn(
            CreateLaunchTemplateVersionResponse.builder()
                .launchTemplateVersion(ltVersionNew)
                .build());
  }

  @DisplayName("Given invalid requests, successfully validate with error messages")
  @Test
  public void modifyServerGroupLaunchTemplate_invalidRequests_expect_validationFailure() {
    final String invalidReqDir = "/modifySgLaunchTemplate_invalid_requests/";
    final String pattern = PATH_PREFIX + invalidReqDir + "*.json";
    TestUtils.loadResourcesFromDir(pattern).stream()
        .forEach(
            ti -> {
              final String testFileName = ti.getFilename();
              System.out.println("\nRunning tests for " + invalidReqDir + testFileName);

              // given
              Map<String, Object> requestBody = TestUtils.loadJson(ti).asMap();

              // when, then
              final String expectedValidationMsg =
                  (testFileName.contains("-")
                          ? StringUtils.substringAfterLast(testFileName, "-")
                          : testFileName)
                      .split(".json")[0];

              given()
                  .contentType(ContentType.JSON)
                  .body(requestBody)
                  .when()
                  .post(getBaseUrl() + UPDATE_LAUNCH_TEMPLATE_OP_PATH)
                  .then()
                  .statusCode(400)
                  .contentType(ContentType.JSON)
                  .assertThat()
                  .body("message", Matchers.equalTo("Validation Failed"))
                  .body("errors.size()", Matchers.equalTo(1))
                  .body("errors[0]", Matchers.endsWith(expectedValidationMsg));
            });
  }

  @DisplayName(
      "Given request to update launch template for a server group NOT backed by launch template, "
          + "throws exception")
  @Test
  public void modifyServerGroupLaunchTemplate_sgWithLaunchConfig_expect_exception()
      throws InterruptedException {
    // given
    Map<String, Object> requestBody =
        TestUtils.loadJson(PATH_PREFIX + "modifyServerGroupLaunchTemplate-basic.json")
            .withValue("instanceType", "c4.large")
            .asMap();
    AutoScalingGroup asgWithLc =
        AutoScalingGroup.builder()
            .autoScalingGroupName(ASG_NAME)
            .launchConfigurationName("some-launch-config")
            .build();
    when(mockAutoScaling.describeAutoScalingGroups(
            DescribeAutoScalingGroupsRequest.builder()
                .autoScalingGroupNames(Collections.singletonList(ASG_NAME))
                .build()))
        .thenReturn(
            DescribeAutoScalingGroupsResponse.builder().autoScalingGroups(asgWithLc).build());

    // when, then
    given()
        .contentType(ContentType.JSON)
        .body(requestBody)
        .when()
        .post(getBaseUrl() + UPDATE_LAUNCH_TEMPLATE_OP_PATH)
        .prettyPrint();
    String taskId =
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post(getBaseUrl() + UPDATE_LAUNCH_TEMPLATE_OP_PATH)
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
            "Orchestration failed: ModifyServerGroupLaunchTemplateAtomicOperation | IllegalArgumentException: "
                + "[Server group is not backed by a launch template.\n"
                + asgWithLc
                + "]"));
  }

  @DisplayName(
      "Given request to update launch template, "
          + "successfully submit update auto scaling group request with expected configuration.")
  @Test
  public void modifyServerGroupLaunchTemplate_sgWithLaunchTemplate_success()
      throws InterruptedException {
    // given
    Map<String, Object> requestBody =
        TestUtils.loadJson(PATH_PREFIX + "modifyServerGroupLaunchTemplate-basic.json")
            .withValue("spotPrice", "0.5")
            .withValue("instanceType", "t3.large")
            .asMap();
    when(mockAutoScaling.describeAutoScalingGroups(
            DescribeAutoScalingGroupsRequest.builder()
                .autoScalingGroupNames(Collections.singletonList(ASG_NAME))
                .build()))
        .thenReturn(
            DescribeAutoScalingGroupsResponse.builder().autoScalingGroups(asgWithLt).build());

    // when, then
    String taskId =
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post(getBaseUrl() + UPDATE_LAUNCH_TEMPLATE_OP_PATH)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("resourceUri", containsString("/task/"))
            .extract()
            .path("id");

    // then
    assertNotNull(getTaskUpdatesAfterCompletion(taskId));

    // capture and assert arguments
    ArgumentCaptor<CreateLaunchTemplateVersionRequest> createLtVersionArgs =
        ArgumentCaptor.forClass(CreateLaunchTemplateVersionRequest.class);
    verify(mockEc2).createLaunchTemplateVersion(createLtVersionArgs.capture());
    CreateLaunchTemplateVersionRequest createLtVersionReq = createLtVersionArgs.getValue();

    assertEquals("lt-1", createLtVersionReq.launchTemplateId());
    assertEquals("ami-12345", createLtVersionReq.launchTemplateData().imageId());
    assertEquals("t3.large", createLtVersionReq.launchTemplateData().instanceTypeAsString());
    assertEquals(
        "spot",
        createLtVersionReq.launchTemplateData().instanceMarketOptions().marketTypeAsString());
    assertEquals(
        "0.5",
        createLtVersionReq.launchTemplateData().instanceMarketOptions().spotOptions().maxPrice());

    ArgumentCaptor<UpdateAutoScalingGroupRequest> updateAsgArgs =
        ArgumentCaptor.forClass(UpdateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).updateAutoScalingGroup(updateAsgArgs.capture());
    UpdateAutoScalingGroupRequest updateAsgReq = updateAsgArgs.getValue();

    assertEquals(ASG_NAME, updateAsgReq.autoScalingGroupName());
    assertEquals("2", updateAsgReq.launchTemplate().version());

    assertNull(updateAsgReq.mixedInstancesPolicy());
  }

  @DisplayName(
      "Given request to update launch template along with mixed instances policy properties, for a server group with launch template, "
          + "creates new launch template version and submits update auto scaling group request with mixed instances policy.")
  @Test
  public void modifyServerGroupLaunchTemplate_ltAndMipFields_createsNewLaunchTemplateVersion()
      throws InterruptedException {
    // given
    Map<String, Object> requestBody =
        TestUtils.loadJson(PATH_PREFIX + "modifyServerGroupLaunchTemplate-basic.json")
            .withValue("unlimitedCpuCredits", true)
            .withValue("spotAllocationStrategy", "capacity-optimized")
            .withValue(
                "launchTemplateOverridesForInstanceType",
                List.of(
                    Map.of("instanceType", "t3.large", "weightedCapacity", "2"),
                    Map.of("instanceType", "t3.xlarge", "weightedCapacity", "4")))
            .asMap();
    when(mockAutoScaling.describeAutoScalingGroups(
            DescribeAutoScalingGroupsRequest.builder()
                .autoScalingGroupNames(Collections.singletonList(ASG_NAME))
                .build()))
        .thenReturn(
            DescribeAutoScalingGroupsResponse.builder().autoScalingGroups(asgWithLt).build());

    ResponseLaunchTemplateData ltData =
        ltVersionNew.launchTemplateData().toBuilder()
            .creditSpecification(CreditSpecification.builder().cpuCredits("unlimited").build())
            .build();
    when(mockEc2.createLaunchTemplateVersion(any(CreateLaunchTemplateVersionRequest.class)))
        .thenReturn(
            CreateLaunchTemplateVersionResponse.builder()
                .launchTemplateVersion(
                    LaunchTemplateVersion.builder()
                        .launchTemplateData(ltData)
                        .launchTemplateId("lt-1")
                        .launchTemplateName("lt-1")
                        .versionNumber(2L)
                        .build())
                .build());

    // when, then
    String taskId =
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post(getBaseUrl() + UPDATE_LAUNCH_TEMPLATE_OP_PATH)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("resourceUri", containsString("/task/"))
            .extract()
            .path("id");

    // then
    assertNotNull(getTaskUpdatesAfterCompletion(taskId));

    // capture and assert arguments
    ArgumentCaptor<CreateLaunchTemplateVersionRequest> createLtVersionArgs =
        ArgumentCaptor.forClass(CreateLaunchTemplateVersionRequest.class);
    verify(mockEc2).createLaunchTemplateVersion(createLtVersionArgs.capture());
    CreateLaunchTemplateVersionRequest createLtVersionReq = createLtVersionArgs.getValue();

    assertEquals("lt-1", createLtVersionReq.launchTemplateId());
    assertEquals(
        "unlimited", createLtVersionReq.launchTemplateData().creditSpecification().cpuCredits());

    ArgumentCaptor<UpdateAutoScalingGroupRequest> updateAsgArgs =
        ArgumentCaptor.forClass(UpdateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).updateAutoScalingGroup(updateAsgArgs.capture());
    UpdateAutoScalingGroupRequest updateAsgReq = updateAsgArgs.getValue();

    assertEquals(ASG_NAME, updateAsgReq.autoScalingGroupName());
    assertNull(updateAsgReq.launchTemplate());

    MixedInstancesPolicy mipInUpdateReq = updateAsgReq.mixedInstancesPolicy();
    assertNotNull(mipInUpdateReq);
    assertEquals(
        "lt-1", mipInUpdateReq.launchTemplate().launchTemplateSpecification().launchTemplateId());
    assertEquals("2", mipInUpdateReq.launchTemplate().launchTemplateSpecification().version());
    assertEquals(
        "capacity-optimized", mipInUpdateReq.instancesDistribution().spotAllocationStrategy());
    assertEquals(
        "[LaunchTemplateOverrides(InstanceType=t3.large, WeightedCapacity=2), LaunchTemplateOverrides(InstanceType=t3.xlarge, WeightedCapacity=4)]",
        mipInUpdateReq.launchTemplate().overrides().toString());
  }

  @DisplayName(
      "Given request to update mixed instances policy properties only, for a server group with launch template and spotMaxPrice set, "
          + "creates new launch template version and submits update auto scaling group request with mixed instances policy.")
  @Test
  public void
      modifyServerGroupLaunchTemplate_convert_SgWithLtSpot_To_SgWithMip_createsNewLaunchTemplateVersion()
          throws InterruptedException {
    // given
    Map<String, Object> requestBody =
        TestUtils.loadJson(PATH_PREFIX + "modifyServerGroupLaunchTemplate-basic.json")
            .withValue("asgName", ASG_NAME)
            .withValue("spotAllocationStrategy", "capacity-optimized")
            .withValue(
                "launchTemplateOverridesForInstanceType",
                List.of(
                    Map.of("instanceType", "t3.large", "weightedCapacity", "2"),
                    Map.of("instanceType", "t3.xlarge", "weightedCapacity", "4")))
            .asMap();

    LaunchTemplateVersion ltVersionOldLocal =
        LaunchTemplateVersion.builder()
            .launchTemplateId("lt-1")
            .launchTemplateName("lt-spot-1")
            .versionNumber(1L)
            .launchTemplateData(
                ResponseLaunchTemplateData.builder()
                    .imageId("ami-12345")
                    .instanceType("c3.large")
                    .instanceMarketOptions(
                        LaunchTemplateInstanceMarketOptions.builder()
                            .marketType("spot")
                            .spotOptions(
                                LaunchTemplateSpotMarketOptions.builder().maxPrice("0.5").build())
                            .build())
                    .build())
            .build();

    LaunchTemplateVersion ltVersionNewLocal =
        LaunchTemplateVersion.builder()
            .launchTemplateId(ltVersionOldLocal.launchTemplateId())
            .launchTemplateName(ltVersionOldLocal.launchTemplateName())
            .versionNumber(2L)
            .launchTemplateData(
                ResponseLaunchTemplateData.builder()
                    .imageId("ami-12345")
                    .instanceType("c3.large")
                    .build())
            .build();

    AutoScalingGroup asgWithLtSpot =
        AutoScalingGroup.builder()
            .autoScalingGroupName(ASG_NAME)
            .launchTemplate(
                LaunchTemplateSpecification.builder()
                    .launchTemplateId(ltVersionOldLocal.launchTemplateId())
                    .version(String.valueOf(ltVersionOldLocal.versionNumber()))
                    .build())
            .build();

    when(mockAutoScaling.describeAutoScalingGroups(
            DescribeAutoScalingGroupsRequest.builder()
                .autoScalingGroupNames(Collections.singletonList(ASG_NAME))
                .build()))
        .thenReturn(
            DescribeAutoScalingGroupsResponse.builder().autoScalingGroups(asgWithLtSpot).build());
    when(mockEc2.describeLaunchTemplateVersions(any(DescribeLaunchTemplateVersionsRequest.class)))
        .thenReturn(
            DescribeLaunchTemplateVersionsResponse.builder()
                .launchTemplateVersions(ltVersionOldLocal)
                .build());
    when(mockEc2.createLaunchTemplateVersion(any(CreateLaunchTemplateVersionRequest.class)))
        .thenReturn(
            CreateLaunchTemplateVersionResponse.builder()
                .launchTemplateVersion(ltVersionNewLocal)
                .build());

    // when, then
    String taskId =
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post(getBaseUrl() + UPDATE_LAUNCH_TEMPLATE_OP_PATH)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("resourceUri", containsString("/task/"))
            .extract()
            .path("id");

    // then
    assertNotNull(getTaskUpdatesAfterCompletion(taskId));

    // capture and assert arguments
    ArgumentCaptor<CreateLaunchTemplateVersionRequest> createLtVersionArgs =
        ArgumentCaptor.forClass(CreateLaunchTemplateVersionRequest.class);
    verify(mockEc2).createLaunchTemplateVersion(createLtVersionArgs.capture());
    CreateLaunchTemplateVersionRequest createLtVersionReq = createLtVersionArgs.getValue();

    assertEquals("lt-1", createLtVersionReq.launchTemplateId());
    assertNull(
        createLtVersionReq
            .launchTemplateData()
            .instanceMarketOptions()); // spotMaxPrice was removed

    ArgumentCaptor<UpdateAutoScalingGroupRequest> updateAsgArgs =
        ArgumentCaptor.forClass(UpdateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).updateAutoScalingGroup(updateAsgArgs.capture());
    UpdateAutoScalingGroupRequest updateAsgReq = updateAsgArgs.getValue();

    assertEquals(ASG_NAME, updateAsgReq.autoScalingGroupName());
    assertNull(
        updateAsgReq.launchTemplate()); // assert updated ASG uses mixed instances policy instead of
    // launch template

    MixedInstancesPolicy mipInUpdateReq = updateAsgReq.mixedInstancesPolicy();
    assertNotNull(mipInUpdateReq);
    assertEquals(
        "lt-1", mipInUpdateReq.launchTemplate().launchTemplateSpecification().launchTemplateId());
    assertEquals("2", mipInUpdateReq.launchTemplate().launchTemplateSpecification().version());
    assertEquals(
        "capacity-optimized", mipInUpdateReq.instancesDistribution().spotAllocationStrategy());
    assertEquals(
        "0.5",
        mipInUpdateReq
            .instancesDistribution()
            .spotMaxPrice()); // spot max price was moved from LTData to MIP
    assertEquals(
        "[LaunchTemplateOverrides(InstanceType=t3.large, WeightedCapacity=2), LaunchTemplateOverrides(InstanceType=t3.xlarge, WeightedCapacity=4)]",
        mipInUpdateReq.launchTemplate().overrides().toString());
  }

  @DisplayName(
      "Given request to modify mixed instances policy fields, for a server group with mixed instances policy, "
          + "successfully skips creating new launch template version and submits update auto scaling group request.")
  @Test
  public void
      modifyMipOnlyFields_sgWithMixedInstancesPolicy_skips_newLaunchTemplateVersionCreation()
          throws InterruptedException {
    // given
    Map<String, Object> requestBody =
        TestUtils.loadJson(PATH_PREFIX + "modifyServerGroupLaunchTemplate-basic.json")
            .withValue("onDemandBaseCapacity", 2)
            .withValue("onDemandPercentageAboveBaseCapacity", 25)
            .withValue("spotAllocationStrategy", "capacity-optimized")
            .withValue(
                "launchTemplateOverridesForInstanceType",
                List.of(
                    Map.of("instanceType", "c3.large", "weightedCapacity", "2"),
                    Map.of("instanceType", "c3.xlarge", "weightedCapacity", "4")))
            .asMap();
    when(mockAutoScaling.describeAutoScalingGroups(
            DescribeAutoScalingGroupsRequest.builder()
                .autoScalingGroupNames(Collections.singletonList(ASG_NAME))
                .build()))
        .thenReturn(
            DescribeAutoScalingGroupsResponse.builder().autoScalingGroups(asgWithMip).build());

    // when, then
    String taskId =
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post(getBaseUrl() + UPDATE_LAUNCH_TEMPLATE_OP_PATH)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("resourceUri", containsString("/task/"))
            .extract()
            .path("id");

    // then
    assertNotNull(getTaskUpdatesAfterCompletion(taskId));

    // verify new launch template version was NOT created
    verify(mockEc2, never())
        .createLaunchTemplateVersion(any(CreateLaunchTemplateVersionRequest.class));

    // capture and assert arguments
    ArgumentCaptor<UpdateAutoScalingGroupRequest> updateAsgArgs =
        ArgumentCaptor.forClass(UpdateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).updateAutoScalingGroup(updateAsgArgs.capture());
    UpdateAutoScalingGroupRequest updateAsgReq = updateAsgArgs.getValue();

    assertEquals(ASG_NAME, updateAsgReq.autoScalingGroupName());
    assertNull(updateAsgReq.launchTemplate());

    MixedInstancesPolicy mipInUpdateReq = updateAsgReq.mixedInstancesPolicy();
    assertNotNull(mipInUpdateReq);
    assertEquals(
        "lt-1", mipInUpdateReq.launchTemplate().launchTemplateSpecification().launchTemplateId());
    assertEquals("1", mipInUpdateReq.launchTemplate().launchTemplateSpecification().version());
    assertEquals(2, mipInUpdateReq.instancesDistribution().onDemandBaseCapacity());
    assertEquals(25, mipInUpdateReq.instancesDistribution().onDemandPercentageAboveBaseCapacity());
    assertEquals(
        "capacity-optimized", mipInUpdateReq.instancesDistribution().spotAllocationStrategy());
    assertEquals(null, mipInUpdateReq.instancesDistribution().spotInstancePools());
    assertEquals(
        "1.5",
        mipInUpdateReq
            .instancesDistribution()
            .spotMaxPrice()); // spot max price in MIP wasn't modified
    assertEquals(
        "[LaunchTemplateOverrides(InstanceType=c3.large, WeightedCapacity=2), LaunchTemplateOverrides(InstanceType=c3.xlarge, WeightedCapacity=4)]",
        mipInUpdateReq.launchTemplate().overrides().toString());
  }

  @DisplayName(
      "Given request update mixed instances policy fields only, for a server group with launch template and NO spot options set, "
          + "successfully skips creating new launch template version and updates auto scaling group request with mixed instances policy.")
  @Test
  public void modifyMipOnlyFields_sgWithLtOnDemand_skips_newLaunchTemplateVersionCreation()
      throws InterruptedException {
    // given
    Map<String, Object> requestBody =
        TestUtils.loadJson(PATH_PREFIX + "modifyServerGroupLaunchTemplate-basic.json")
            .withValue("spotPrice", "0.5")
            .withValue("spotAllocationStrategy", "lowest-price")
            .withValue("spotInstancePools", "6")
            .withValue(
                "launchTemplateOverridesForInstanceType",
                List.of(
                    Map.of("instanceType", "c3.large", "weightedCapacity", "2"),
                    Map.of("instanceType", "c4.large", "weightedCapacity", "2"),
                    Map.of("instanceType", "c4.xlarge", "weightedCapacity", "4"),
                    Map.of("instanceType", "c3.xlarge", "weightedCapacity", "4")))
            .asMap();
    when(mockAutoScaling.describeAutoScalingGroups(
            DescribeAutoScalingGroupsRequest.builder()
                .autoScalingGroupNames(Collections.singletonList(ASG_NAME))
                .build()))
        .thenReturn(
            DescribeAutoScalingGroupsResponse.builder().autoScalingGroups(asgWithLt).build());

    // when, then
    String taskId =
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post(getBaseUrl() + UPDATE_LAUNCH_TEMPLATE_OP_PATH)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("resourceUri", containsString("/task/"))
            .extract()
            .path("id");

    // then
    assertNotNull(getTaskUpdatesAfterCompletion(taskId));

    // verify new launch template version was NOT created
    verify(mockEc2, never())
        .createLaunchTemplateVersion(any(CreateLaunchTemplateVersionRequest.class));

    // capture and assert arguments
    ArgumentCaptor<UpdateAutoScalingGroupRequest> updateAsgArgs =
        ArgumentCaptor.forClass(UpdateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).updateAutoScalingGroup(updateAsgArgs.capture());
    UpdateAutoScalingGroupRequest updateAsgReq = updateAsgArgs.getValue();

    assertEquals(ASG_NAME, updateAsgReq.autoScalingGroupName());
    assertNull(updateAsgReq.launchTemplate());

    MixedInstancesPolicy mipInUpdateReq = updateAsgReq.mixedInstancesPolicy();
    assertNotNull(mipInUpdateReq);
    assertEquals(
        "lt-1", mipInUpdateReq.launchTemplate().launchTemplateSpecification().launchTemplateId());
    assertEquals("1", mipInUpdateReq.launchTemplate().launchTemplateSpecification().version());
    assertEquals("lowest-price", mipInUpdateReq.instancesDistribution().spotAllocationStrategy());
    assertEquals(6, mipInUpdateReq.instancesDistribution().spotInstancePools());
    assertEquals("0.5", mipInUpdateReq.instancesDistribution().spotMaxPrice());
    assertEquals(
        "[LaunchTemplateOverrides(InstanceType=c3.large, WeightedCapacity=2), LaunchTemplateOverrides(InstanceType=c4.large, WeightedCapacity=2), LaunchTemplateOverrides(InstanceType=c4.xlarge, WeightedCapacity=4), LaunchTemplateOverrides(InstanceType=c3.xlarge, WeightedCapacity=4)]",
        mipInUpdateReq.launchTemplate().overrides().toString());
  }

  @DisplayName(
      "Given request to modify spot max price, for a server group with mixed instances policy, "
          + "successfully skips creating new launch template version and submits update auto scaling group request.")
  @Test
  public void modifySpotPrice_sgWithMixedInstancesPolicy_skips_newLaunchTemplateVersionCreation()
      throws InterruptedException {
    // given
    Map<String, Object> requestBody =
        TestUtils.loadJson(PATH_PREFIX + "modifyServerGroupLaunchTemplate-basic.json")
            .withValue("spotPrice", "2")
            .asMap();
    when(mockAutoScaling.describeAutoScalingGroups(
            DescribeAutoScalingGroupsRequest.builder()
                .autoScalingGroupNames(Collections.singletonList(ASG_NAME))
                .build()))
        .thenReturn(
            DescribeAutoScalingGroupsResponse.builder().autoScalingGroups(asgWithMip).build());

    // when, then
    String taskId =
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post(getBaseUrl() + UPDATE_LAUNCH_TEMPLATE_OP_PATH)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("resourceUri", containsString("/task/"))
            .extract()
            .path("id");

    // then
    assertNotNull(getTaskUpdatesAfterCompletion(taskId));

    // verify new launch template version creation was SKIPPED
    verify(mockEc2, never())
        .createLaunchTemplateVersion(any(CreateLaunchTemplateVersionRequest.class));

    // capture and assert arguments
    ArgumentCaptor<UpdateAutoScalingGroupRequest> updateAsgArgs =
        ArgumentCaptor.forClass(UpdateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).updateAutoScalingGroup(updateAsgArgs.capture());
    UpdateAutoScalingGroupRequest updateAsgReq = updateAsgArgs.getValue();

    assertEquals(ASG_NAME, updateAsgReq.autoScalingGroupName());
    assertNull(updateAsgReq.launchTemplate());

    MixedInstancesPolicy mipInUpdateReq = updateAsgReq.mixedInstancesPolicy();
    assertNotNull(mipInUpdateReq);
    assertEquals("2", mipInUpdateReq.instancesDistribution().spotMaxPrice());
  }

  @DisplayName(
      "Given request to modify launch template, and new launch template version is created successfully, but update AutoScalingGroup fails, "
          + "successfully deletes newly created launch template version to maintain atomicity.")
  @Test
  public void modifyLaunchTemplate_newLaunchTemplateVersionCreated_andDeleted_onUpdateFailure()
      throws InterruptedException {
    // given
    Map<String, Object> requestBody =
        TestUtils.loadJson(PATH_PREFIX + "modifyServerGroupLaunchTemplate-basic.json")
            .withValue("instanceType", "t3.large")
            .asMap();
    when(mockAutoScaling.describeAutoScalingGroups(
            DescribeAutoScalingGroupsRequest.builder()
                .autoScalingGroupNames(Collections.singletonList(ASG_NAME))
                .build()))
        .thenReturn(
            DescribeAutoScalingGroupsResponse.builder().autoScalingGroups(asgWithLt).build());

    UpdateAutoScalingGroupRequest updateAsgReq =
        UpdateAutoScalingGroupRequest.builder()
            .autoScalingGroupName(ASG_NAME)
            .launchTemplate(
                LaunchTemplateSpecification.builder()
                    .launchTemplateId(ltVersionNew.launchTemplateId())
                    .version(String.valueOf(ltVersionNew.versionNumber()))
                    .build())
            .build();
    when(mockAutoScaling.updateAutoScalingGroup(updateAsgReq))
        .thenThrow(AutoScalingException.builder().message("Something went wrong.").build());

    when(mockEc2.deleteLaunchTemplateVersions(
            DeleteLaunchTemplateVersionsRequest.builder()
                .launchTemplateId(ltVersionNew.launchTemplateId())
                .versions(String.valueOf(ltVersionNew.versionNumber()))
                .build()))
        .thenReturn(
            DeleteLaunchTemplateVersionsResponse.builder()
                .successfullyDeletedLaunchTemplateVersions(
                    DeleteLaunchTemplateVersionsResponseSuccessItem.builder()
                        .launchTemplateId(ltVersionNew.launchTemplateId())
                        .versionNumber(ltVersionNew.versionNumber())
                        .build())
                .build());

    // when, then
    String taskId =
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post(getBaseUrl() + UPDATE_LAUNCH_TEMPLATE_OP_PATH)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("resourceUri", containsString("/task/"))
            .extract()
            .path("id");

    // then
    assertNotNull(getTaskUpdatesAfterCompletion(taskId));
    final String taskHistory = getTaskUpdatesAfterCompletion(taskId);
    assertTrue(
        taskHistory.contains(
            "Orchestration failed: ModifyServerGroupLaunchTemplateAtomicOperation | LaunchTemplateException: [Failed to update server group myasg.Error: Something went wrong."));

    // verify new launch template version was created
    verify(mockEc2).createLaunchTemplateVersion(any(CreateLaunchTemplateVersionRequest.class));

    // verify updateAutoScalingGroup throws exception
    assertThrows(
        AutoScalingException.class, () -> mockAutoScaling.updateAutoScalingGroup(updateAsgReq));

    // verify newly create launch template version was deleted
    ArgumentCaptor<DeleteLaunchTemplateVersionsRequest> deleteLtVersionArgs =
        ArgumentCaptor.forClass(DeleteLaunchTemplateVersionsRequest.class);
    verify(mockEc2).deleteLaunchTemplateVersions(deleteLtVersionArgs.capture());
    DeleteLaunchTemplateVersionsRequest deleteLtVersionReq = deleteLtVersionArgs.getValue();

    assertEquals(ltVersionNew.launchTemplateId(), deleteLtVersionReq.launchTemplateId());
    assertEquals(1, deleteLtVersionReq.versions().size());
    assertEquals(
        String.valueOf(ltVersionNew.versionNumber()), deleteLtVersionReq.versions().get(0));
  }

  @DisplayName(
      "Given request to modify launch template, and new launch template version is created successfully, but update AutoScalingGroup fails, "
          + "and delete of newly created launch template version fails, exception is reported correctly.")
  @Test()
  public void
      modifyLaunchTemplate_onUpdateFailure_andDeletionOfLtVersionFailure_exceptionReportedCorrectly()
          throws InterruptedException {
    // given
    Map<String, Object> requestBody =
        TestUtils.loadJson(PATH_PREFIX + "modifyServerGroupLaunchTemplate-basic.json")
            .withValue("instanceType", "t3.large")
            .asMap();
    when(mockAutoScaling.describeAutoScalingGroups(
            DescribeAutoScalingGroupsRequest.builder()
                .autoScalingGroupNames(Collections.singletonList(ASG_NAME))
                .build()))
        .thenReturn(
            DescribeAutoScalingGroupsResponse.builder().autoScalingGroups(asgWithLt).build());

    UpdateAutoScalingGroupRequest updateAsgReq =
        UpdateAutoScalingGroupRequest.builder()
            .autoScalingGroupName(ASG_NAME)
            .launchTemplate(
                LaunchTemplateSpecification.builder()
                    .launchTemplateId(ltVersionNew.launchTemplateId())
                    .version(String.valueOf(ltVersionNew.versionNumber()))
                    .build())
            .build();
    when(mockAutoScaling.updateAutoScalingGroup(updateAsgReq))
        .thenThrow(AutoScalingException.builder().build());

    when(mockEc2.deleteLaunchTemplateVersions(
            DeleteLaunchTemplateVersionsRequest.builder()
                .launchTemplateId(ltVersionNew.launchTemplateId())
                .versions(String.valueOf(ltVersionNew.versionNumber()))
                .build()))
        .thenReturn(
            DeleteLaunchTemplateVersionsResponse.builder()
                .unsuccessfullyDeletedLaunchTemplateVersions(
                    DeleteLaunchTemplateVersionsResponseErrorItem.builder()
                        .launchTemplateId(ltVersionNew.launchTemplateId())
                        .versionNumber(ltVersionNew.versionNumber())
                        .responseError(ResponseError.builder().code("unexpectedError").build())
                        .build())
                .build());

    // when, then
    String taskId =
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post(getBaseUrl() + UPDATE_LAUNCH_TEMPLATE_OP_PATH)
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
            "Orchestration failed: ModifyServerGroupLaunchTemplateAtomicOperation | LaunchTemplateException: "
                + "[Failed to update server group myasg."));
    assertTrue(
        taskHistory.contains(
            "Failed to clean up launch template version! Error: Failed to delete launch template version 2 for launch template ID lt-1 because of error 'unexpectedError'"));

    // verify new launch template version was created
    verify(mockEc2).createLaunchTemplateVersion(any(CreateLaunchTemplateVersionRequest.class));

    // verify updateAutoScalingGroup throws exception
    assertThrows(
        AutoScalingException.class, () -> mockAutoScaling.updateAutoScalingGroup(updateAsgReq));

    // verify newly create launch template version was attempted to be deleted
    ArgumentCaptor<DeleteLaunchTemplateVersionsRequest> deleteLtVersionArgs =
        ArgumentCaptor.forClass(DeleteLaunchTemplateVersionsRequest.class);
    verify(mockEc2).deleteLaunchTemplateVersions(deleteLtVersionArgs.capture());
    DeleteLaunchTemplateVersionsRequest deleteLtVersionReq = deleteLtVersionArgs.getValue();

    assertEquals(ltVersionNew.launchTemplateId(), deleteLtVersionReq.launchTemplateId());
    assertEquals(1, deleteLtVersionReq.versions().size());
    assertEquals(
        String.valueOf(ltVersionNew.versionNumber()), deleteLtVersionReq.versions().get(0));
  }
}
