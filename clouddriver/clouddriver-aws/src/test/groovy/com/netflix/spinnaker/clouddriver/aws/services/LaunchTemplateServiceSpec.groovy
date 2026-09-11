/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.aws.services

import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.*
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.AmazonResourceTagger
import com.netflix.spinnaker.clouddriver.aws.deploy.DefaultAmazonResourceTagger
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AutoScalingWorker
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ModifyServerGroupLaunchTemplateDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.LocalFileUserDataProperties
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.UserDataProviderAggregator
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class LaunchTemplateServiceSpec extends Specification {
  private static final String LT_ID_1 = "lt-1"
  private static final String USER_DATA_STR = "my-userdata"

  def mockEc2 = Mock(Ec2Client)
  def mockUserDataAggregator = Mock(UserDataProviderAggregator)

  @Shared
  NetflixAmazonCredentials testCredentials = TestCredential.named('test')

  @Subject
  @Shared
  def launchTemplateService

  def setup() {
    mockUserDataAggregator.aggregate(_) >> USER_DATA_STR

    launchTemplateService = new LaunchTemplateService(
      mockEc2,
      mockUserDataAggregator,
      Mock(LocalFileUserDataProperties),
      null
    )
  }

  @Unroll
  void 'should match ebs encryption'() {
    when:
    def result = launchTemplateService.getLaunchTemplateEbsBlockDeviceRequest(blockDevice)

    then:
    result.encrypted() == encrypted && result.kmsKeyId() == kmsKeyId

    where:
    blockDevice                                             | encrypted | kmsKeyId
    new AmazonBlockDevice()                                 | null      | null
    new AmazonBlockDevice(encrypted: true) | true | null
    new AmazonBlockDevice(encrypted: true, kmsKeyId: "xxx") | true      | "xxx"
  }

  @Unroll
  void 'matches throughput'() {
    when:
    def result = launchTemplateService.getLaunchTemplateEbsBlockDeviceRequest(blockDevice)

    then:
    result.throughput() == blockDevice.getThroughput()

    where:
    blockDevice                            | _
    new AmazonBlockDevice(throughput: 250) | _
  }

  @Unroll
  void 'should generate volume tags'() {
    given:
    launchTemplateService = new LaunchTemplateService(
      mockEc2,
      mockUserDataAggregator,
      Mock(LocalFileUserDataProperties),
      Collections.singletonList(
        new DefaultAmazonResourceTagger("spinnaker:application", "spinnaker:cluster")
      ))

    expect:
    launchTemplateService.tagSpecification(
      amazonResourceTaggers,
      ["blockKey": "blockValue"],
      "application-stack-details-v001"
    ) == result

    where:
    amazonResourceTaggers << [
      null,
      [],
      [new AmazonResourceTagger() {}],
      [new DefaultAmazonResourceTagger("spinnaker:application", "spinnaker:cluster")]
    ]
    result << [
      Optional.empty(),
      Optional.empty(),
      Optional.empty(),
      Optional.of(
        LaunchTemplateTagSpecificationRequest.builder()
          .resourceType("volume")
          .tags([
            Tag.builder().key("spinnaker:application").value("application").build(),
            Tag.builder().key("spinnaker:cluster").value("application-stack-details").build(),
            Tag.builder().key("blockKey").value("blockValue").build()
          ])
          .build()
      )
    ]
  }

  @Unroll
  void 'should create launch template data with expected configuration, for create operation'() {
    given:
    def asgConfig = AutoScalingWorker.AsgConfiguration.builder()
      .setLaunchTemplate(true)
      .credentials(testCredentials)
      .legacyUdf(false)
      .application("myasg-001")
      .region("us-east-1")
      .minInstances(1)
      .maxInstances(3)
      .desiredInstances(2)
      .instanceType("some.type.medium")
      .securityGroups(["my-sg"])
      .ami("ami-1")
      .kernelId("kernel-id-1")
      .ramdiskId("ramdisk-id-1")
      .ebsOptimized(true)
      .keyPair("my-key-name")
      .iamRole("my-iam-role")
      .instanceMonitoring(true)
      .base64UserData(USER_DATA_STR)
      .requireIMDSv2(true)
      .spotMaxPrice("0.5")
      .unlimitedCpuCredits(true)
      .associatePublicIpAddress(true)
      .associateIPv6Address(true)
      .blockDevices([new AmazonBlockDevice(deviceName: "/dev/sdb", size: 40, volumeType: "standard")])
      .enableEnclave(true)
      .spotAllocationStrategy(spotAllocationStrategy)
      .build()

    def ltDataBuilder = RequestLaunchTemplateData.builder()
      .imageId("ami-1")
      .kernelId("kernel-id-1")
      .instanceType("some.type.medium")
      .ramDiskId("ramdisk-id-1")
      .ebsOptimized(true)
      .keyName("my-key-name")
      .iamInstanceProfile(LaunchTemplateIamInstanceProfileSpecificationRequest.builder().name("my-iam-role").build())
      .monitoring(LaunchTemplatesMonitoringRequest.builder().enabled(true).build())
      .userData(USER_DATA_STR)
      .metadataOptions(LaunchTemplateInstanceMetadataOptionsRequest.builder().httpTokens("required").build())
      .creditSpecification(CreditSpecificationRequest.builder().cpuCredits("unlimited").build())
      .networkInterfaces([
        LaunchTemplateInstanceNetworkInterfaceSpecificationRequest.builder()
          .deviceIndex(0)
          .groups(["my-sg"])
          .associatePublicIpAddress(true)
          .ipv6AddressCount(1)
          .build()
      ])
      .blockDeviceMappings([
        LaunchTemplateBlockDeviceMappingRequest.builder()
          .deviceName("/dev/sdb")
          .ebs(LaunchTemplateEbsBlockDeviceRequest.builder().volumeSize(40).volumeType("standard").build())
          .build()
      ])
      .enclaveOptions(LaunchTemplateEnclaveOptionsRequest.builder().enabled(true).build())
    if (setSpotOptions) {
      ltDataBuilder.instanceMarketOptions(LaunchTemplateInstanceMarketOptionsRequest.builder()
        .marketType(MarketType.SPOT)
        .spotOptions(LaunchTemplateSpotMarketOptionsRequest.builder().maxPrice("0.5").build())
        .build())
    }
    def expectedLtDataInReq = ltDataBuilder.build()

    when:
    launchTemplateService.createLaunchTemplate(asgConfig, "myasg-001", "my-lt-001")

    then:
    1 * mockEc2.createLaunchTemplate(_ as CreateLaunchTemplateRequest) >> { arguments ->
      // assert arguments passed and return dummy result
      CreateLaunchTemplateRequest reqInArg = arguments[0]
      assert reqInArg.launchTemplateName() == "my-lt-001" && reqInArg.launchTemplateData() == expectedLtDataInReq
      CreateLaunchTemplateResponse.builder()
        .launchTemplate(LaunchTemplate.builder()
          .launchTemplateId(LT_ID_1)
          .launchTemplateName("my-lt-001")
          .defaultVersionNumber(1L)
          .latestVersionNumber(1L)
          .build())
        .build()
    }

    where:
    spotAllocationStrategy|| setSpotOptions
    "capacity-optimized"  ||    false
            null          ||    true
  }

  @Unroll
  void 'should generate launch template data for modify operation, with precedence given to description values first and then to source version values'() {
    given:
    def modifyDesc = new ModifyServerGroupLaunchTemplateDescription(
      region: "us-east-1",
      asgName: "myasg",
      amiName: "ami-1",
      credentials: testCredentials,
      spotPrice: maxSpotPrice,
      instanceType: instanceType,
      securityGroups: secGroupsInDesc,
    )

    def srcLtDataBuilder = ResponseLaunchTemplateData.builder()
      .imageId("ami-1")
      .kernelId("kernel-id-1")
      .instanceType("t2.large")
      .ramDiskId("ramdisk-id-1")
      .ebsOptimized(true)
      .keyName("my-key-name")
      .iamInstanceProfile(LaunchTemplateIamInstanceProfileSpecification.builder().name("my-iam-role").build())
      .monitoring(LaunchTemplatesMonitoring.builder().enabled(true).build())
      .userData(USER_DATA_STR)
      .metadataOptions(LaunchTemplateInstanceMetadataOptions.builder().httpTokens("required").build())
      .instanceMarketOptions(LaunchTemplateInstanceMarketOptions.builder().marketType("spot").spotOptions(LaunchTemplateSpotMarketOptions.builder().maxPrice("0.5").build()).build())
      .creditSpecification(CreditSpecification.builder().cpuCredits("standard").build())
      .networkInterfaces([
        LaunchTemplateInstanceNetworkInterfaceSpecification.builder()
          .deviceIndex(0)
          .groups(secGroupsInSrc)
          .associatePublicIpAddress(true)
          .ipv6AddressCount(1)
          .build()
      ])
      .blockDeviceMappings([
        LaunchTemplateBlockDeviceMapping.builder()
          .deviceName("/dev/sdb")
          .ebs(LaunchTemplateEbsBlockDevice.builder().volumeSize(40).build())
          .build()
      ])
      .enclaveOptions(LaunchTemplateEnclaveOptions.builder().enabled(true).build())
    def srcLtVersionDataRespWithSpotOptions = srcLtDataBuilder.build()

    def sourceLtVersion = LaunchTemplateVersion.builder()
      .launchTemplateId(LT_ID_1)
      .versionNumber(1L)
      .launchTemplateData(srcLtVersionDataRespWithSpotOptions)
      .build()

    // RequestLaunchTemplateData built in the class under test
    def expectedBuilder = RequestLaunchTemplateData.builder()
      .imageId("ami-1")
      .kernelId("kernel-id-1")
      .instanceType(instanceType)
      .ramDiskId("ramdisk-id-1")
      .ebsOptimized(true)
      .keyName("my-key-name")
      .iamInstanceProfile(LaunchTemplateIamInstanceProfileSpecificationRequest.builder().name("my-iam-role").build())
      .monitoring(LaunchTemplatesMonitoringRequest.builder().enabled(true).build())
      .userData(USER_DATA_STR)
      .metadataOptions(LaunchTemplateInstanceMetadataOptionsRequest.builder().httpTokens("required").build())
      .networkInterfaces([
        LaunchTemplateInstanceNetworkInterfaceSpecificationRequest.builder()
          .deviceIndex(0)
          .groups(expectedSecGroups)
          .associatePublicIpAddress(true)
          .ipv6AddressCount(1)
          .build()
      ])
      .blockDeviceMappings([
        LaunchTemplateBlockDeviceMappingRequest.builder()
          .deviceName("/dev/sdb")
          .ebs(LaunchTemplateEbsBlockDeviceRequest.builder().volumeSize(40).build())
          .build()
      ])
      .enclaveOptions(LaunchTemplateEnclaveOptionsRequest.builder().enabled(true).build())
    if (setSpotOptions) {
      expectedBuilder.instanceMarketOptions(LaunchTemplateInstanceMarketOptionsRequest.builder()
        .marketType(MarketType.SPOT)
        .spotOptions(LaunchTemplateSpotMarketOptionsRequest.builder().maxPrice("0.5").build())
        .build())
    }
    if (copyCpuCreditSpecFromSrc) {
      expectedBuilder.creditSpecification(CreditSpecificationRequest.builder().cpuCredits("standard").build())
    }
    def expectedNewLtVersionDataReq = expectedBuilder.build()

    when:
    launchTemplateService.modifyLaunchTemplate(testCredentials, modifyDesc, sourceLtVersion, shouldUseMixedInstancesPolicy)

    then:
    1 * mockEc2.createLaunchTemplateVersion(_ as CreateLaunchTemplateVersionRequest) >> { arguments ->
      // assert arguments passed and return dummy result
      CreateLaunchTemplateVersionRequest reqInArg = arguments[0]
      assert reqInArg.launchTemplateId() == LT_ID_1 && reqInArg.launchTemplateData() == expectedNewLtVersionDataReq
      CreateLaunchTemplateVersionResponse.builder()
        .launchTemplateVersion(LaunchTemplateVersion.builder()
          .launchTemplateId(LT_ID_1)
          .versionNumber(2L)
          .launchTemplateData(ResponseLaunchTemplateData.builder().build())
          .build())
        .build()
    }

    where:
    shouldUseMixedInstancesPolicy | maxSpotPrice || setSpotOptions | instanceType  || copyCpuCreditSpecFromSrc | secGroupsInDesc  | secGroupsInSrc || expectedSecGroups
            true                  |     _        ||     false      |  't3.large'   ||     true                 |    ["new-sg-2"]  |  ["src-sg-1"]  ||  ["new-sg-2"]
            false                 |     ""       ||     false      |  'c3.large'   ||     false                |         []       |  ["src-sg-1"]  ||  ["src-sg-1"]
            false                 |   null       ||     false      |  't3.large'   ||     true                 |        null      |  ["src-sg-1"]  ||  ["src-sg-1"]
            false                 |   "0.5"      ||      true      |  'm5.large'   ||     false                |        null      |       null     ||     null
  }

  @Unroll
  void 'delete launch template version success scenarios are handled as expected'() {
    given:
    def versionToDelete = 2L

    DeleteLaunchTemplateVersionsResponseSuccessItem successItem = ltIdSuccess
      ? DeleteLaunchTemplateVersionsResponseSuccessItem.builder()
          .launchTemplateId(ltIdSuccess)
          .versionNumber(versionToDelete)
          .build()
      : null

    DeleteLaunchTemplateVersionsResponseErrorItem errorItem = ltIdFailure
      ? DeleteLaunchTemplateVersionsResponseErrorItem.builder()
          .launchTemplateId(ltIdFailure)
          .versionNumber(versionToDelete)
          .responseError(ResponseError.builder().code(errorCode).build())
          .build()
      : null

    DeleteLaunchTemplateVersionsResponse result = DeleteLaunchTemplateVersionsResponse.builder()
      .successfullyDeletedLaunchTemplateVersions(successItem ? [successItem] : [])
      .unsuccessfullyDeletedLaunchTemplateVersions(errorItem ? [errorItem] : [])
      .build()

    when:
    launchTemplateService.deleteLaunchTemplateVersion(LT_ID_1, versionToDelete)

    then:
    1 * mockEc2.deleteLaunchTemplateVersions(DeleteLaunchTemplateVersionsRequest.builder()
      .launchTemplateId(LT_ID_1)
      .versions(String.valueOf(versionToDelete))
      .build()) >> result

    and:
    noExceptionThrown()

    where:
    ltIdSuccess | ltIdFailure |            errorCode
    LT_ID_1     |     null    |              null                    // success
    null        |   LT_ID_1   |  "launchTemplateIdDoesNotExist"      // failed with error code considered success
    null        |   LT_ID_1   | "launchTemplateVersionDoesNotExist"  // failed with error code considered success
  }

  @Unroll
  void 'delete launch template version should handle errors as expected'() {
    given:
    def versionToDelete = 2L

    DeleteLaunchTemplateVersionsResponseSuccessItem successItem = ltIdSuccess
      ? DeleteLaunchTemplateVersionsResponseSuccessItem.builder()
          .launchTemplateId(ltIdSuccess)
          .versionNumber(versionToDelete)
          .build()
      : null

    DeleteLaunchTemplateVersionsResponseErrorItem errorItem = ltIdFailure
      ? DeleteLaunchTemplateVersionsResponseErrorItem.builder()
          .launchTemplateId(ltIdFailure)
          .versionNumber(versionToDelete)
          .responseError(ResponseError.builder().code(errorCode).build())
          .build()
      : null

    DeleteLaunchTemplateVersionsResponse result = DeleteLaunchTemplateVersionsResponse.builder()
      .successfullyDeletedLaunchTemplateVersions(successItem ? [successItem] : [])
      .unsuccessfullyDeletedLaunchTemplateVersions(errorItem ? [errorItem] : [])
      .build()

    when:
    launchTemplateService.deleteLaunchTemplateVersion(LT_ID_1, versionToDelete)

    then:
    1 * mockEc2.deleteLaunchTemplateVersions(DeleteLaunchTemplateVersionsRequest.builder()
      .launchTemplateId(LT_ID_1)
      .versions(String.valueOf(versionToDelete))
      .build()) >> result

    and:
    def ex = thrown(RuntimeException)
    errorCode
      ? ex.message == "Failed to delete launch template version 2 for launch template ID lt-1 because of error '" + errorCode + "'"
      : ex == null

    where:
    ltIdSuccess | ltIdFailure |     errorCode
      null      |  LT_ID_1    |  "unexpectedError"
      null      |  LT_ID_1    |  "launchTemplateIdMalformed"
  }
}
