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

import com.amazonaws.services.ec2.model.CreateLaunchTemplateRequest
import com.amazonaws.services.ec2.model.CreateLaunchTemplateResult
import com.amazonaws.services.ec2.model.CreateLaunchTemplateVersionRequest
import com.amazonaws.services.ec2.model.CreateLaunchTemplateVersionResult
import com.amazonaws.services.ec2.model.CreditSpecification
import com.amazonaws.services.ec2.model.CreditSpecificationRequest
import com.amazonaws.services.ec2.model.DeleteLaunchTemplateVersionsRequest
import com.amazonaws.services.ec2.model.DeleteLaunchTemplateVersionsResponseErrorItem
import com.amazonaws.services.ec2.model.DeleteLaunchTemplateVersionsResponseSuccessItem
import com.amazonaws.services.ec2.model.DeleteLaunchTemplateVersionsResult
import com.amazonaws.services.ec2.model.LaunchTemplate
import com.amazonaws.services.ec2.model.LaunchTemplateBlockDeviceMapping
import com.amazonaws.services.ec2.model.LaunchTemplateBlockDeviceMappingRequest
import com.amazonaws.services.ec2.model.LaunchTemplateEbsBlockDevice
import com.amazonaws.services.ec2.model.LaunchTemplateEbsBlockDeviceRequest
import com.amazonaws.services.ec2.model.LaunchTemplateEnclaveOptions
import com.amazonaws.services.ec2.model.LaunchTemplateEnclaveOptionsRequest
import com.amazonaws.services.ec2.model.LaunchTemplateIamInstanceProfileSpecification
import com.amazonaws.services.ec2.model.LaunchTemplateIamInstanceProfileSpecificationRequest
import com.amazonaws.services.ec2.model.LaunchTemplateInstanceMarketOptions
import com.amazonaws.services.ec2.model.LaunchTemplateInstanceMarketOptionsRequest
import com.amazonaws.services.ec2.model.LaunchTemplateInstanceMetadataOptions
import com.amazonaws.services.ec2.model.LaunchTemplateInstanceMetadataOptionsRequest
import com.amazonaws.services.ec2.model.LaunchTemplateInstanceNetworkInterfaceSpecification
import com.amazonaws.services.ec2.model.LaunchTemplateInstanceNetworkInterfaceSpecificationRequest
import com.amazonaws.services.ec2.model.LaunchTemplateSpotMarketOptions
import com.amazonaws.services.ec2.model.LaunchTemplateSpotMarketOptionsRequest
import com.amazonaws.services.ec2.model.LaunchTemplateTagSpecificationRequest
import com.amazonaws.services.ec2.model.LaunchTemplateVersion
import com.amazonaws.services.ec2.model.LaunchTemplatesMonitoring
import com.amazonaws.services.ec2.model.LaunchTemplatesMonitoringRequest
import com.amazonaws.services.ec2.model.RequestLaunchTemplateData
import com.amazonaws.services.ec2.model.ResponseError
import com.amazonaws.services.ec2.model.ResponseLaunchTemplateData
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.Tag
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

  def mockEc2 = Mock(AmazonEC2)
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
    result.getEncrypted() == encrypted && result.getKmsKeyId() == kmsKeyId

    where:
    blockDevice                                             | encrypted | kmsKeyId
    new AmazonBlockDevice()                                 | null      | null
    new AmazonBlockDevice(encrypted: true) | true | null
    new AmazonBlockDevice(encrypted: true, kmsKeyId: "xxx") | true      | "xxx"
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
        new LaunchTemplateTagSpecificationRequest()
        .withResourceType("volume")
          .withTags([
            new Tag("spinnaker:application", "application"),
            new Tag("spinnaker:cluster", "application-stack-details"),
            new Tag("blockKey", "blockValue")
          ])
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

    def expectedLtDataInReq = new RequestLaunchTemplateData(
      imageId: "ami-1",
      kernelId: "kernel-id-1",
      instanceType: "some.type.medium",
      ramDiskId: "ramdisk-id-1",
      ebsOptimized: true,
      keyName: "my-key-name",
      iamInstanceProfile: new LaunchTemplateIamInstanceProfileSpecificationRequest().withName("my-iam-role"),
      monitoring: new LaunchTemplatesMonitoringRequest().withEnabled(true),
      userData: USER_DATA_STR,
      metadataOptions: new LaunchTemplateInstanceMetadataOptionsRequest().withHttpTokens("required"),
      instanceMarketOptions:
        setSpotOptions
          ? new LaunchTemplateInstanceMarketOptionsRequest().withMarketType("spot").withSpotOptions(new LaunchTemplateSpotMarketOptionsRequest().withMaxPrice("0.5"))
          : null,
      creditSpecification: new CreditSpecificationRequest().withCpuCredits("unlimited"),
      networkInterfaces: [
        new LaunchTemplateInstanceNetworkInterfaceSpecificationRequest(
          deviceIndex: 0,
          groups: ["my-sg"],
          associatePublicIpAddress: true,
          ipv6AddressCount: 1
        )
      ],
      blockDeviceMappings: [
        new LaunchTemplateBlockDeviceMappingRequest(
          deviceName: "/dev/sdb",
          ebs: new LaunchTemplateEbsBlockDeviceRequest(volumeSize: 40, volumeType: "standard")
        )
      ],
      enclaveOptions: new LaunchTemplateEnclaveOptionsRequest().withEnabled(true)
    )

    when:
    launchTemplateService.createLaunchTemplate(asgConfig, "myasg-001", "my-lt-001")

    then:
    1 * mockEc2.createLaunchTemplate(_ as CreateLaunchTemplateRequest) >> { arguments ->
      // assert arguments passed and return dummy result
      CreateLaunchTemplateRequest reqInArg = arguments[0]
      assert reqInArg.launchTemplateName == "my-lt-001" && reqInArg.launchTemplateData == expectedLtDataInReq ; new CreateLaunchTemplateResult()
        .withLaunchTemplate(new LaunchTemplate(
          launchTemplateId: LT_ID_1,
          launchTemplateName: "my-lt-001",
          defaultVersionNumber: 1L,
          latestVersionNumber: 1L))
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

    def srcLtVersionDataRespWithSpotOptions = new ResponseLaunchTemplateData(
      imageId: "ami-1",
      kernelId: "kernel-id-1",
      instanceType: "t2.large",
      ramDiskId: "ramdisk-id-1",
      ebsOptimized: true,
      keyName: "my-key-name",
      iamInstanceProfile: new LaunchTemplateIamInstanceProfileSpecification().withName("my-iam-role"),
      monitoring: new LaunchTemplatesMonitoring().withEnabled(true),
      userData: USER_DATA_STR,
      metadataOptions: new LaunchTemplateInstanceMetadataOptions().withHttpTokens("required"),
      instanceMarketOptions: new LaunchTemplateInstanceMarketOptions().withMarketType("spot").withSpotOptions(new LaunchTemplateSpotMarketOptions().withMaxPrice("0.5")),
      creditSpecification: new CreditSpecification().withCpuCredits("standard"),
      networkInterfaces: [
        new LaunchTemplateInstanceNetworkInterfaceSpecification(
          deviceIndex: 0,
          groups: secGroupsInSrc,
          associatePublicIpAddress: true,
          ipv6AddressCount: 1
        )
      ],
      blockDeviceMappings: [
        new LaunchTemplateBlockDeviceMapping(
          deviceName: "/dev/sdb",
          ebs: new LaunchTemplateEbsBlockDevice(volumeSize: 40)
        )
      ],
      enclaveOptions: new LaunchTemplateEnclaveOptions().withEnabled(true)
    )

    def sourceLtVersion = new LaunchTemplateVersion(
      launchTemplateId: LT_ID_1,
      versionNumber: 1,
      launchTemplateData: srcLtVersionDataRespWithSpotOptions
    )

    // RequestLaunchTemplateData built in the class under test
    def expectedNewLtVersionDataReq = new RequestLaunchTemplateData(
      imageId: "ami-1",
      kernelId: "kernel-id-1",
      instanceType: instanceType,
      ramDiskId: "ramdisk-id-1",
      ebsOptimized: true,
      keyName: "my-key-name",
      iamInstanceProfile: new LaunchTemplateIamInstanceProfileSpecificationRequest().withName("my-iam-role"),
      monitoring: new LaunchTemplatesMonitoringRequest().withEnabled(true),
      userData: USER_DATA_STR,
      metadataOptions: new LaunchTemplateInstanceMetadataOptionsRequest().withHttpTokens("required"),
      instanceMarketOptions:
        setSpotOptions
        ? new LaunchTemplateInstanceMarketOptionsRequest().withMarketType("spot").withSpotOptions(new LaunchTemplateSpotMarketOptionsRequest().withMaxPrice("0.5"))
        : null,
      creditSpecification:
        copyCpuCreditSpecFromSrc ? new CreditSpecificationRequest().withCpuCredits("standard") : null,
      networkInterfaces: [
        new LaunchTemplateInstanceNetworkInterfaceSpecificationRequest(
          deviceIndex: 0,
          groups: expectedSecGroups,
          associatePublicIpAddress: true,
          ipv6AddressCount: 1
        )
      ],
      blockDeviceMappings: [
        new LaunchTemplateBlockDeviceMappingRequest(
          deviceName: "/dev/sdb",
          ebs: new LaunchTemplateEbsBlockDeviceRequest(volumeSize: 40)
        )
      ],
      enclaveOptions: new LaunchTemplateEnclaveOptionsRequest().withEnabled(true)
    )

    when:
    launchTemplateService.modifyLaunchTemplate(testCredentials, modifyDesc, sourceLtVersion, shouldUseMixedInstancesPolicy)

    then:
    1 * mockEc2.createLaunchTemplateVersion(_ as CreateLaunchTemplateVersionRequest) >> { arguments ->
      // assert arguments passed and return dummy result
      CreateLaunchTemplateVersionRequest reqInArg = arguments[0]
      assert reqInArg.launchTemplateId == LT_ID_1 && reqInArg.launchTemplateData == expectedNewLtVersionDataReq ; new CreateLaunchTemplateVersionResult()
        .withLaunchTemplateVersion(new LaunchTemplateVersion(
          launchTemplateId: LT_ID_1,
          versionNumber: 2L,
          launchTemplateData: new ResponseLaunchTemplateData()))
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
      ? new DeleteLaunchTemplateVersionsResponseSuccessItem()
      .withLaunchTemplateId(ltIdSuccess)
      .withVersionNumber(versionToDelete)
      : null

    DeleteLaunchTemplateVersionsResponseErrorItem errorItem = ltIdFailure
      ? new DeleteLaunchTemplateVersionsResponseErrorItem()
      .withLaunchTemplateId(ltIdFailure)
      .withVersionNumber(versionToDelete)
      .withResponseError(new ResponseError().withCode(errorCode))
      : null

    DeleteLaunchTemplateVersionsResult result = new DeleteLaunchTemplateVersionsResult()
      .withSuccessfullyDeletedLaunchTemplateVersions(successItem)
      .withUnsuccessfullyDeletedLaunchTemplateVersions(errorItem)

    when:
    launchTemplateService.deleteLaunchTemplateVersion(LT_ID_1, versionToDelete)

    then:
    1 * mockEc2.deleteLaunchTemplateVersions(new DeleteLaunchTemplateVersionsRequest()
      .withLaunchTemplateId(LT_ID_1)
      .withVersions(String.valueOf(versionToDelete))) >> result

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
      ? new DeleteLaunchTemplateVersionsResponseSuccessItem()
          .withLaunchTemplateId(ltIdSuccess)
          .withVersionNumber(versionToDelete)
      : null

    DeleteLaunchTemplateVersionsResponseErrorItem errorItem = ltIdFailure
      ? new DeleteLaunchTemplateVersionsResponseErrorItem()
          .withLaunchTemplateId(ltIdFailure)
          .withVersionNumber(versionToDelete)
          .withResponseError(new ResponseError().withCode(errorCode))
      : null

    DeleteLaunchTemplateVersionsResult result = new DeleteLaunchTemplateVersionsResult()
      .withSuccessfullyDeletedLaunchTemplateVersions(successItem)
      .withUnsuccessfullyDeletedLaunchTemplateVersions(errorItem)

    when:
    launchTemplateService.deleteLaunchTemplateVersion(LT_ID_1, versionToDelete)

    then:
    1 * mockEc2.deleteLaunchTemplateVersions(new DeleteLaunchTemplateVersionsRequest()
      .withLaunchTemplateId(LT_ID_1)
      .withVersions(String.valueOf(versionToDelete))) >> result

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
