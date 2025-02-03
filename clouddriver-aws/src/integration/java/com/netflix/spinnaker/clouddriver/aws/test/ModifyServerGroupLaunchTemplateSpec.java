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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.model.AmazonAutoScalingException;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.InstancesDistribution;
import com.amazonaws.services.autoscaling.model.LaunchTemplateOverrides;
import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification;
import com.amazonaws.services.autoscaling.model.MixedInstancesPolicy;
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateVersionRequest;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateVersionResult;
import com.amazonaws.services.ec2.model.CreditSpecification;
import com.amazonaws.services.ec2.model.DeleteLaunchTemplateVersionsRequest;
import com.amazonaws.services.ec2.model.DeleteLaunchTemplateVersionsResponseErrorItem;
import com.amazonaws.services.ec2.model.DeleteLaunchTemplateVersionsResponseSuccessItem;
import com.amazonaws.services.ec2.model.DeleteLaunchTemplateVersionsResult;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.DescribeLaunchTemplateVersionsRequest;
import com.amazonaws.services.ec2.model.DescribeLaunchTemplateVersionsResult;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.services.ec2.model.LaunchTemplateInstanceMarketOptions;
import com.amazonaws.services.ec2.model.LaunchTemplateSpotMarketOptions;
import com.amazonaws.services.ec2.model.LaunchTemplateVersion;
import com.amazonaws.services.ec2.model.ResponseError;
import com.amazonaws.services.ec2.model.ResponseLaunchTemplateData;
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

/**
 * Test class for general test cases related to CreateServerGroup operation. Note: launch template
 * settings are enabled in clouddriver.yml
 */
@ActiveProfiles("launch-templates")
public class ModifyServerGroupLaunchTemplateSpec extends AwsBaseSpec {
  @Autowired ApplicationContext context;

  private AsgService mockAsgService = mock(AsgService.class);
  private AmazonEC2 mockEc2 = mock(AmazonEC2.class);
  private AmazonAutoScaling mockAutoScaling = mock(AmazonAutoScaling.class);

  private static final String ASG_NAME = "myasg";

  // ASG with Launch Template
  private final LaunchTemplateVersion ltVersionOld =
      new LaunchTemplateVersion()
          .withLaunchTemplateId("lt-1")
          .withLaunchTemplateName("lt-1")
          .withVersionNumber(1L)
          .withLaunchTemplateData(
              new ResponseLaunchTemplateData()
                  .withImageId("ami-12345")
                  .withInstanceType("t3.large"));

  private final LaunchTemplateVersion ltVersionNew =
      new LaunchTemplateVersion()
          .withLaunchTemplateId("lt-1")
          .withLaunchTemplateName("lt-1")
          .withVersionNumber(2L)
          .withLaunchTemplateData(
              new ResponseLaunchTemplateData()
                  .withImageId("ami-12345")
                  .withInstanceType("t3.large"));

  private final AutoScalingGroup asgWithLt =
      new AutoScalingGroup()
          .withAutoScalingGroupName(ASG_NAME)
          .withLaunchTemplate(
              new LaunchTemplateSpecification()
                  .withLaunchTemplateId(ltVersionOld.getLaunchTemplateId())
                  .withVersion(String.valueOf(ltVersionOld.getVersionNumber())));

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
          new LaunchTemplateOverrides()
              .withInstanceType(override1.getInstanceType())
              .withWeightedCapacity(override1.getWeightedCapacity()),
          new LaunchTemplateOverrides()
              .withInstanceType(override2.getInstanceType())
              .withWeightedCapacity(override2.getWeightedCapacity()));
  InstancesDistribution instancesDist =
      new InstancesDistribution()
          .withOnDemandBaseCapacity(1)
          .withOnDemandPercentageAboveBaseCapacity(50)
          .withSpotInstancePools(5)
          .withSpotAllocationStrategy("lowest-price")
          .withSpotMaxPrice("1.5");
  private final AutoScalingGroup asgWithMip =
      new AutoScalingGroup()
          .withAutoScalingGroupName(ASG_NAME)
          .withMixedInstancesPolicy(
              new MixedInstancesPolicy()
                  .withLaunchTemplate(
                      new com.amazonaws.services.autoscaling.model.LaunchTemplate()
                          .withOverrides(ltOverrides)
                          .withLaunchTemplateSpecification(
                              new LaunchTemplateSpecification()
                                  .withLaunchTemplateId(ltVersionOld.getLaunchTemplateId())
                                  .withVersion("$Latest")))
                  .withInstancesDistribution(instancesDist));

  @BeforeEach
  public void setup() {

    // mock autoscaling responses
    when(mockAwsClientProvider.getAutoScaling(
            any(NetflixAmazonCredentials.class), anyString(), anyBoolean()))
        .thenReturn(mockAutoScaling);
    when(mockRegionScopedProvider.getAsgService()).thenReturn(mockAsgService);

    // mock Front50 service responses
    Map applicationMap = new HashMap();
    applicationMap.put("application", "myAwsApp");
    applicationMap.put("legacyUdf", null);
    when(mockFront50Service.getApplication(ASG_NAME)).thenReturn(Calls.response(applicationMap));

    // mock EC2 responses
    when(mockRegionScopedProvider.getAmazonEC2()).thenReturn(mockEc2);
    when(mockAwsClientProvider.getAmazonEC2(
            any(NetflixAmazonCredentials.class), anyString(), anyBoolean()))
        .thenReturn(mockEc2);
    when(mockEc2.describeLaunchTemplateVersions(any(DescribeLaunchTemplateVersionsRequest.class)))
        .thenReturn(
            new DescribeLaunchTemplateVersionsResult().withLaunchTemplateVersions(ltVersionOld));
    when(mockEc2.describeImages(any(DescribeImagesRequest.class)))
        .thenReturn(new DescribeImagesResult().withImages(new Image().withImageId("ami-12345")));
    when(mockEc2.createLaunchTemplateVersion(any(CreateLaunchTemplateVersionRequest.class)))
        .thenReturn(
            new CreateLaunchTemplateVersionResult().withLaunchTemplateVersion(ltVersionNew));
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
        new AutoScalingGroup()
            .withAutoScalingGroupName(ASG_NAME)
            .withLaunchConfigurationName("some-launch-config");
    when(mockAutoScaling.describeAutoScalingGroups(
            new DescribeAutoScalingGroupsRequest()
                .withAutoScalingGroupNames(Collections.singletonList(ASG_NAME))))
        .thenReturn(new DescribeAutoScalingGroupsResult().withAutoScalingGroups(asgWithLc));

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
            new DescribeAutoScalingGroupsRequest()
                .withAutoScalingGroupNames(Collections.singletonList(ASG_NAME))))
        .thenReturn(new DescribeAutoScalingGroupsResult().withAutoScalingGroups(asgWithLt));

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

    assertEquals("lt-1", createLtVersionReq.getLaunchTemplateId());
    assertEquals("ami-12345", createLtVersionReq.getLaunchTemplateData().getImageId());
    assertEquals("t3.large", createLtVersionReq.getLaunchTemplateData().getInstanceType());
    assertEquals(
        "spot",
        createLtVersionReq.getLaunchTemplateData().getInstanceMarketOptions().getMarketType());
    assertEquals(
        "0.5",
        createLtVersionReq
            .getLaunchTemplateData()
            .getInstanceMarketOptions()
            .getSpotOptions()
            .getMaxPrice());

    ArgumentCaptor<UpdateAutoScalingGroupRequest> updateAsgArgs =
        ArgumentCaptor.forClass(UpdateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).updateAutoScalingGroup(updateAsgArgs.capture());
    UpdateAutoScalingGroupRequest updateAsgReq = updateAsgArgs.getValue();

    assertEquals(ASG_NAME, updateAsgReq.getAutoScalingGroupName());
    assertEquals("2", updateAsgReq.getLaunchTemplate().getVersion());

    assertNull(updateAsgReq.getMixedInstancesPolicy());
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
            new DescribeAutoScalingGroupsRequest()
                .withAutoScalingGroupNames(Collections.singletonList(ASG_NAME))))
        .thenReturn(new DescribeAutoScalingGroupsResult().withAutoScalingGroups(asgWithLt));

    ResponseLaunchTemplateData ltData =
        ltVersionNew
            .getLaunchTemplateData()
            .withCreditSpecification(new CreditSpecification().withCpuCredits("unlimited"));
    when(mockEc2.createLaunchTemplateVersion(any(CreateLaunchTemplateVersionRequest.class)))
        .thenReturn(
            new CreateLaunchTemplateVersionResult()
                .withLaunchTemplateVersion(
                    new LaunchTemplateVersion()
                        .withLaunchTemplateData(ltData)
                        .withLaunchTemplateId("lt-1")
                        .withLaunchTemplateName("lt-1")
                        .withVersionNumber(2L)));

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

    assertEquals("lt-1", createLtVersionReq.getLaunchTemplateId());
    assertEquals(
        "unlimited",
        createLtVersionReq.getLaunchTemplateData().getCreditSpecification().getCpuCredits());

    ArgumentCaptor<UpdateAutoScalingGroupRequest> updateAsgArgs =
        ArgumentCaptor.forClass(UpdateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).updateAutoScalingGroup(updateAsgArgs.capture());
    UpdateAutoScalingGroupRequest updateAsgReq = updateAsgArgs.getValue();

    assertEquals(ASG_NAME, updateAsgReq.getAutoScalingGroupName());
    assertNull(updateAsgReq.getLaunchTemplate());

    MixedInstancesPolicy mipInUpdateReq = updateAsgReq.getMixedInstancesPolicy();
    assertNotNull(mipInUpdateReq);
    assertEquals(
        "lt-1",
        mipInUpdateReq.getLaunchTemplate().getLaunchTemplateSpecification().getLaunchTemplateId());
    assertEquals(
        "2", mipInUpdateReq.getLaunchTemplate().getLaunchTemplateSpecification().getVersion());
    assertEquals(
        "capacity-optimized",
        mipInUpdateReq.getInstancesDistribution().getSpotAllocationStrategy());
    assertEquals(
        "[{InstanceType: t3.large,WeightedCapacity: 2,}, {InstanceType: t3.xlarge,WeightedCapacity: 4,}]",
        mipInUpdateReq.getLaunchTemplate().getOverrides().toString());
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
        new LaunchTemplateVersion()
            .withLaunchTemplateId("lt-1")
            .withLaunchTemplateName("lt-spot-1")
            .withVersionNumber(1L)
            .withLaunchTemplateData(
                new ResponseLaunchTemplateData()
                    .withImageId("ami-12345")
                    .withInstanceType("c3.large")
                    .withInstanceMarketOptions(
                        new LaunchTemplateInstanceMarketOptions()
                            .withMarketType("spot")
                            .withSpotOptions(
                                new LaunchTemplateSpotMarketOptions().withMaxPrice("0.5"))));

    LaunchTemplateVersion ltVersionNewLocal =
        new LaunchTemplateVersion()
            .withLaunchTemplateId(ltVersionOldLocal.getLaunchTemplateId())
            .withLaunchTemplateName(ltVersionOldLocal.getLaunchTemplateName())
            .withVersionNumber(2L)
            .withLaunchTemplateData(
                new ResponseLaunchTemplateData()
                    .withImageId("ami-12345")
                    .withInstanceType("c3.large"));

    AutoScalingGroup asgWithLtSpot =
        new AutoScalingGroup()
            .withAutoScalingGroupName(ASG_NAME)
            .withLaunchTemplate(
                new LaunchTemplateSpecification()
                    .withLaunchTemplateId(ltVersionOldLocal.getLaunchTemplateId())
                    .withVersion(String.valueOf(ltVersionOldLocal.getVersionNumber())));

    when(mockAutoScaling.describeAutoScalingGroups(
            new DescribeAutoScalingGroupsRequest()
                .withAutoScalingGroupNames(Collections.singletonList(ASG_NAME))))
        .thenReturn(new DescribeAutoScalingGroupsResult().withAutoScalingGroups(asgWithLtSpot));
    when(mockEc2.describeLaunchTemplateVersions(any(DescribeLaunchTemplateVersionsRequest.class)))
        .thenReturn(
            new DescribeLaunchTemplateVersionsResult()
                .withLaunchTemplateVersions(ltVersionOldLocal));
    when(mockEc2.createLaunchTemplateVersion(any(CreateLaunchTemplateVersionRequest.class)))
        .thenReturn(
            new CreateLaunchTemplateVersionResult().withLaunchTemplateVersion(ltVersionNewLocal));

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

    assertEquals("lt-1", createLtVersionReq.getLaunchTemplateId());
    assertNull(
        createLtVersionReq
            .getLaunchTemplateData()
            .getInstanceMarketOptions()); // spotMaxPrice was removed

    ArgumentCaptor<UpdateAutoScalingGroupRequest> updateAsgArgs =
        ArgumentCaptor.forClass(UpdateAutoScalingGroupRequest.class);
    verify(mockAutoScaling).updateAutoScalingGroup(updateAsgArgs.capture());
    UpdateAutoScalingGroupRequest updateAsgReq = updateAsgArgs.getValue();

    assertEquals(ASG_NAME, updateAsgReq.getAutoScalingGroupName());
    assertNull(
        updateAsgReq
            .getLaunchTemplate()); // assert updated ASG uses mixed instances policy instead of
    // launch template

    MixedInstancesPolicy mipInUpdateReq = updateAsgReq.getMixedInstancesPolicy();
    assertNotNull(mipInUpdateReq);
    assertEquals(
        "lt-1",
        mipInUpdateReq.getLaunchTemplate().getLaunchTemplateSpecification().getLaunchTemplateId());
    assertEquals(
        "2", mipInUpdateReq.getLaunchTemplate().getLaunchTemplateSpecification().getVersion());
    assertEquals(
        "capacity-optimized",
        mipInUpdateReq.getInstancesDistribution().getSpotAllocationStrategy());
    assertEquals(
        "0.5",
        mipInUpdateReq
            .getInstancesDistribution()
            .getSpotMaxPrice()); // spot max price was moved from LTData to MIP
    assertEquals(
        "[{InstanceType: t3.large,WeightedCapacity: 2,}, {InstanceType: t3.xlarge,WeightedCapacity: 4,}]",
        mipInUpdateReq.getLaunchTemplate().getOverrides().toString());
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
            new DescribeAutoScalingGroupsRequest()
                .withAutoScalingGroupNames(Collections.singletonList(ASG_NAME))))
        .thenReturn(new DescribeAutoScalingGroupsResult().withAutoScalingGroups(asgWithMip));

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

    assertEquals(ASG_NAME, updateAsgReq.getAutoScalingGroupName());
    assertNull(updateAsgReq.getLaunchTemplate());

    MixedInstancesPolicy mipInUpdateReq = updateAsgReq.getMixedInstancesPolicy();
    assertNotNull(mipInUpdateReq);
    assertEquals(
        "lt-1",
        mipInUpdateReq.getLaunchTemplate().getLaunchTemplateSpecification().getLaunchTemplateId());
    assertEquals(
        "1", mipInUpdateReq.getLaunchTemplate().getLaunchTemplateSpecification().getVersion());
    assertEquals(2, mipInUpdateReq.getInstancesDistribution().getOnDemandBaseCapacity());
    assertEquals(
        25, mipInUpdateReq.getInstancesDistribution().getOnDemandPercentageAboveBaseCapacity());
    assertEquals(
        "capacity-optimized",
        mipInUpdateReq.getInstancesDistribution().getSpotAllocationStrategy());
    assertEquals(null, mipInUpdateReq.getInstancesDistribution().getSpotInstancePools());
    assertEquals(
        "1.5",
        mipInUpdateReq
            .getInstancesDistribution()
            .getSpotMaxPrice()); // spot max price in MIP wasn't modified
    assertEquals(
        "[{InstanceType: c3.large,WeightedCapacity: 2,}, {InstanceType: c3.xlarge,WeightedCapacity: 4,}]",
        mipInUpdateReq.getLaunchTemplate().getOverrides().toString());
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
            new DescribeAutoScalingGroupsRequest()
                .withAutoScalingGroupNames(Collections.singletonList(ASG_NAME))))
        .thenReturn(new DescribeAutoScalingGroupsResult().withAutoScalingGroups(asgWithLt));

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

    assertEquals(ASG_NAME, updateAsgReq.getAutoScalingGroupName());
    assertNull(updateAsgReq.getLaunchTemplate());

    MixedInstancesPolicy mipInUpdateReq = updateAsgReq.getMixedInstancesPolicy();
    assertNotNull(mipInUpdateReq);
    assertEquals(
        "lt-1",
        mipInUpdateReq.getLaunchTemplate().getLaunchTemplateSpecification().getLaunchTemplateId());
    assertEquals(
        "1", mipInUpdateReq.getLaunchTemplate().getLaunchTemplateSpecification().getVersion());
    assertEquals(
        "lowest-price", mipInUpdateReq.getInstancesDistribution().getSpotAllocationStrategy());
    assertEquals(6, mipInUpdateReq.getInstancesDistribution().getSpotInstancePools());
    assertEquals("0.5", mipInUpdateReq.getInstancesDistribution().getSpotMaxPrice());
    assertEquals(
        "[{InstanceType: c3.large,WeightedCapacity: 2,}, {InstanceType: c4.large,WeightedCapacity: 2,}, {InstanceType: c4.xlarge,WeightedCapacity: 4,}, {InstanceType: c3.xlarge,WeightedCapacity: 4,}]",
        mipInUpdateReq.getLaunchTemplate().getOverrides().toString());
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
            new DescribeAutoScalingGroupsRequest()
                .withAutoScalingGroupNames(Collections.singletonList(ASG_NAME))))
        .thenReturn(new DescribeAutoScalingGroupsResult().withAutoScalingGroups(asgWithMip));

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

    assertEquals(ASG_NAME, updateAsgReq.getAutoScalingGroupName());
    assertNull(updateAsgReq.getLaunchTemplate());

    MixedInstancesPolicy mipInUpdateReq = updateAsgReq.getMixedInstancesPolicy();
    assertNotNull(mipInUpdateReq);
    assertEquals("2", mipInUpdateReq.getInstancesDistribution().getSpotMaxPrice());
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
            new DescribeAutoScalingGroupsRequest()
                .withAutoScalingGroupNames(Collections.singletonList(ASG_NAME))))
        .thenReturn(new DescribeAutoScalingGroupsResult().withAutoScalingGroups(asgWithLt));

    UpdateAutoScalingGroupRequest updateAsgReq =
        new UpdateAutoScalingGroupRequest()
            .withAutoScalingGroupName(ASG_NAME)
            .withLaunchTemplate(
                new LaunchTemplateSpecification()
                    .withLaunchTemplateId(ltVersionNew.getLaunchTemplateId())
                    .withVersion(String.valueOf(ltVersionNew.getVersionNumber())));
    when(mockAutoScaling.updateAutoScalingGroup(updateAsgReq))
        .thenThrow(new AmazonAutoScalingException("Something went wrong."));

    when(mockEc2.deleteLaunchTemplateVersions(
            new DeleteLaunchTemplateVersionsRequest()
                .withLaunchTemplateId(ltVersionNew.getLaunchTemplateId())
                .withVersions(String.valueOf(ltVersionNew.getVersionNumber()))))
        .thenReturn(
            new DeleteLaunchTemplateVersionsResult()
                .withSuccessfullyDeletedLaunchTemplateVersions(
                    new DeleteLaunchTemplateVersionsResponseSuccessItem()
                        .withLaunchTemplateId(ltVersionNew.getLaunchTemplateId())
                        .withVersionNumber(ltVersionNew.getVersionNumber())));

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
        AmazonAutoScalingException.class,
        () -> mockAutoScaling.updateAutoScalingGroup(updateAsgReq));

    // verify newly create launch template version was deleted
    ArgumentCaptor<DeleteLaunchTemplateVersionsRequest> deleteLtVersionArgs =
        ArgumentCaptor.forClass(DeleteLaunchTemplateVersionsRequest.class);
    verify(mockEc2).deleteLaunchTemplateVersions(deleteLtVersionArgs.capture());
    DeleteLaunchTemplateVersionsRequest deleteLtVersionReq = deleteLtVersionArgs.getValue();

    assertEquals(ltVersionNew.getLaunchTemplateId(), deleteLtVersionReq.getLaunchTemplateId());
    assertEquals(1, deleteLtVersionReq.getVersions().size());
    assertEquals(
        String.valueOf(ltVersionNew.getVersionNumber()), deleteLtVersionReq.getVersions().get(0));
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
            new DescribeAutoScalingGroupsRequest()
                .withAutoScalingGroupNames(Collections.singletonList(ASG_NAME))))
        .thenReturn(new DescribeAutoScalingGroupsResult().withAutoScalingGroups(asgWithLt));

    UpdateAutoScalingGroupRequest updateAsgReq =
        new UpdateAutoScalingGroupRequest()
            .withAutoScalingGroupName(ASG_NAME)
            .withLaunchTemplate(
                new LaunchTemplateSpecification()
                    .withLaunchTemplateId(ltVersionNew.getLaunchTemplateId())
                    .withVersion(String.valueOf(ltVersionNew.getVersionNumber())));
    when(mockAutoScaling.updateAutoScalingGroup(updateAsgReq))
        .thenThrow(AmazonAutoScalingException.class);

    when(mockEc2.deleteLaunchTemplateVersions(
            new DeleteLaunchTemplateVersionsRequest()
                .withLaunchTemplateId(ltVersionNew.getLaunchTemplateId())
                .withVersions(String.valueOf(ltVersionNew.getVersionNumber()))))
        .thenReturn(
            new DeleteLaunchTemplateVersionsResult()
                .withUnsuccessfullyDeletedLaunchTemplateVersions(
                    new DeleteLaunchTemplateVersionsResponseErrorItem()
                        .withLaunchTemplateId(ltVersionNew.getLaunchTemplateId())
                        .withVersionNumber(ltVersionNew.getVersionNumber())
                        .withResponseError(new ResponseError().withCode("unexpectedError"))));

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
                + "[Failed to update server group myasg.Error: null"));
    assertTrue(
        taskHistory.contains(
            "Failed to clean up launch template version! Error: Failed to delete launch template version 2 for launch template ID lt-1 because of error 'unexpectedError'"));

    // verify new launch template version was created
    verify(mockEc2).createLaunchTemplateVersion(any(CreateLaunchTemplateVersionRequest.class));

    // verify updateAutoScalingGroup throws exception
    assertThrows(
        AmazonAutoScalingException.class,
        () -> mockAutoScaling.updateAutoScalingGroup(updateAsgReq));

    // verify newly create launch template version was attempted to be deleted
    ArgumentCaptor<DeleteLaunchTemplateVersionsRequest> deleteLtVersionArgs =
        ArgumentCaptor.forClass(DeleteLaunchTemplateVersionsRequest.class);
    verify(mockEc2).deleteLaunchTemplateVersions(deleteLtVersionArgs.capture());
    DeleteLaunchTemplateVersionsRequest deleteLtVersionReq = deleteLtVersionArgs.getValue();

    assertEquals(ltVersionNew.getLaunchTemplateId(), deleteLtVersionReq.getLaunchTemplateId());
    assertEquals(1, deleteLtVersionReq.getVersions().size());
    assertEquals(
        String.valueOf(ltVersionNew.getVersionNumber()), deleteLtVersionReq.getVersions().get(0));
  }
}
