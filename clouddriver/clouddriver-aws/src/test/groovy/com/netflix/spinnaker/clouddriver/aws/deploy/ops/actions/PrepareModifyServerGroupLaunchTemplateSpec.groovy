package com.netflix.spinnaker.clouddriver.aws.deploy.ops.actions

import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup
import software.amazon.awssdk.services.autoscaling.model.InstancesDistribution
import software.amazon.awssdk.services.autoscaling.model.LaunchTemplateSpecification
import software.amazon.awssdk.services.autoscaling.model.MixedInstancesPolicy
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.*
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.InstanceTypeUtils.BlockDeviceConfig
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ModifyServerGroupLaunchTemplateDescription
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.clouddriver.aws.services.AsgService
import com.netflix.spinnaker.clouddriver.aws.services.LaunchTemplateService
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class PrepareModifyServerGroupLaunchTemplateSpec extends Specification {
  private static final String LT_ID_1 = "lt-1", LT_ID_1_V = "1"

  def credentials = TestCredential.named("test")
  def ltService = Mock(LaunchTemplateService)
  def asgService = Mock(AsgService)
  def ec2 = Mock(Ec2Client)
  def blockDeviceConfig = Mock(BlockDeviceConfig)
  def credentialsRepository = Stub(MapBackedCredentialsRepository) {
    getOne(_) >> credentials
  }

  def autoScalingGroupWithLt = AutoScalingGroup.builder()
    .autoScalingGroupName("test-v001")
    .launchTemplate(LaunchTemplateSpecification.builder().launchTemplateName(LT_ID_1).version(LT_ID_1_V).build())
    .build()

  def regionScopedProvider = Stub(RegionScopedProviderFactory.RegionScopedProvider) {
    getAmazonEC2() >> ec2
    getAsgService() >> asgService
    getLaunchTemplateService() >> ltService
  }

  def regionScopedProviderFactory = Mock(RegionScopedProviderFactory) {
    forRegion(_, _) >> regionScopedProvider
  }

  @Subject
  def prepareAction = new PrepareModifyServerGroupLaunchTemplate(
    blockDeviceConfig, credentialsRepository, regionScopedProviderFactory)

  ModifyServerGroupLaunchTemplateDescription modifyDescription
  def prepareCommand

  def setup() {
    modifyDescription = new ModifyServerGroupLaunchTemplateDescription(
      region: "us-east-1",
      asgName: autoScalingGroupWithLt.autoScalingGroupName,
      credentials: credentials)

    prepareCommand = new PrepareModifyServerGroupLaunchTemplate.PrepareModifyServerGroupLaunchTemplateCommand.PrepareModifyServerGroupLaunchTemplateCommandBuilder().description(modifyDescription).build()
  }

  def "should prepare for launch template update"() {
    given:
    modifyDescription.instanceType = "c5.large"

    and:
    def ltVersionBeforeModify = LaunchTemplateVersion.builder()
      .launchTemplateName(LT_ID_1)
      .versionNumber(Long.valueOf(LT_ID_1_V))
      .launchTemplateData(ResponseLaunchTemplateData.builder()
        .imageId("ami-1")
        .instanceType("c3.large")
        .build())
      .build()

    when:
    prepareAction.apply(prepareCommand, new Saga("test", "test"))

    then:
    1 * asgService.getAutoScalingGroup("test-v001") >> autoScalingGroupWithLt
    1 * ltService.getLaunchTemplateVersion(autoScalingGroupWithLt.launchTemplate) >> Optional.of(ltVersionBeforeModify)
    1 * blockDeviceConfig.getBlockDevicesForInstanceType("c5.large")
    1 * blockDeviceConfig.getBlockDevicesForInstanceType("c3.large")
  }

  @Unroll
  def "should prepare for launch template and ASG update for a server group backed by mixed instances policy"() {
    given:
    modifyDescription.spotAllocationStrategy = spotAllocationStrategy   // Mixed instances policy property
    modifyDescription.instanceType = instanceType                       // Launch template property
    modifyDescription.spotPrice = spotPrice                             // Mixed instances policy property as ASG is backed by MIP

    and:
    def mixedInstancesPolicy = MixedInstancesPolicy.builder()
      .launchTemplate(software.amazon.awssdk.services.autoscaling.model.LaunchTemplate.builder()
        .launchTemplateSpecification(autoScalingGroupWithLt.launchTemplate)
        .overrides([
          software.amazon.awssdk.services.autoscaling.model.LaunchTemplateOverrides.builder().instanceType("c3.large").weightedCapacity("2").build(),
          software.amazon.awssdk.services.autoscaling.model.LaunchTemplateOverrides.builder().instanceType("c3.xlarge").weightedCapacity("4").build()
        ])
        .build())
      .instancesDistribution(InstancesDistribution.builder()
        .onDemandAllocationStrategy("prioritized")
        .onDemandBaseCapacity(2)
        .onDemandPercentageAboveBaseCapacity(50)
        .spotAllocationStrategy("lowest-price")
        .spotInstancePools(2)
        .spotMaxPrice("1")
        .build())
      .build()
    def autoScalingGroup = AutoScalingGroup.builder()
      .autoScalingGroupName("test-v001")
      .mixedInstancesPolicy(mixedInstancesPolicy)
      .build()

    def ltVersionBeforeModify = LaunchTemplateVersion.builder()
      .launchTemplateName(LT_ID_1)
      .versionNumber(Long.valueOf(LT_ID_1_V))
      .launchTemplateData(ResponseLaunchTemplateData.builder()
        .imageId("ami-1")
        .instanceType("m5.large")
        .build())
      .build()

    when:
    SagaAction.Result result = prepareAction.apply(prepareCommand, new Saga("test", "test"))

    then:
    1 * asgService.getAutoScalingGroup("test-v001") >> autoScalingGroup
    1 * ltService.getLaunchTemplateVersion(autoScalingGroup.mixedInstancesPolicy.launchTemplate.launchTemplateSpecification) >> Optional.of(ltVersionBeforeModify)
    if (!expectedToSkipStep) {
      1 * blockDeviceConfig.getBlockDevicesForInstanceType("c3.large")
      1 * blockDeviceConfig.getBlockDevicesForInstanceType("m5.large")
    }

    and:
    def nextCommand = ((ModifyServerGroupLaunchTemplate.ModifyServerGroupLaunchTemplateCommand) result.nextCommand)
    nextCommand instanceof ModifyServerGroupLaunchTemplate.ModifyServerGroupLaunchTemplateCommand
    nextCommand.isReqToModifyLaunchTemplate == !expectedToSkipStep
    nextCommand.isAsgBackedByMixedInstancesPolicy == true
    nextCommand.isReqToUpgradeAsgToMixedInstancesPolicy == false

    // assert description fields
    nextCommand.description.spotAllocationStrategy == expectedSpotALlocStrategy
    nextCommand.description.spotInstancePools == expectedSpotInstancePools
    nextCommand.description.onDemandAllocationStrategy == mixedInstancesPolicy.instancesDistribution().onDemandAllocationStrategy()
    nextCommand.description.onDemandBaseCapacity == mixedInstancesPolicy.instancesDistribution().onDemandBaseCapacity()
    nextCommand.description.onDemandPercentageAboveBaseCapacity == mixedInstancesPolicy.instancesDistribution().onDemandPercentageAboveBaseCapacity()

    where:
        spotAllocationStrategy      | spotPrice | instanceType||    expectedSpotALlocStrategy   || expectedSpotInstancePools || expectedToSkipStep
            "capacity-optimized"    |    null   |  "c3.large" ||     "capacity-optimized"       ||          null             ||     false         // isReqToModifyMipFieldsOnly is false
                    null            |    "1"    |  "c3.large" ||        "lowest-price"          ||            2              ||     false         // isReqToModifyMipFieldsOnly is false
              "lowest-price"        |    "1"    |  "c3.large" ||        "lowest-price"          ||            2              ||     false         // isReqToModifyMipFieldsOnly is false
    "capacity-optimized-prioritized"|   null    |      null   ||"capacity-optimized-prioritized"||          null             ||     true          // isReqToModifyMipFieldsOnly is true
                    null            |    "1"    |      null   ||         "lowest-price"         ||            2              ||     true          // isReqToModifyMipFieldsOnly is true
  }

  @Unroll
  def "should prepare for launch template and ASG update for a server group backed by launch template and to be updated to use mixed instances policy"() {
    given:
    modifyDescription.spotAllocationStrategy = spotAllocationStrategy
    modifyDescription.spotPrice = newSpotPrice

    and:
    def autoScalingGroup = AutoScalingGroup.builder()
      .autoScalingGroupName("test-v001")
      .launchTemplate(LaunchTemplateSpecification.builder().launchTemplateName(LT_ID_1).version(LT_ID_1_V).build())
      .build()

    def ltVersionDataBuilder = ResponseLaunchTemplateData.builder().imageId("ami-1")
    if (asgHasSpotLt) {
      ltVersionDataBuilder.instanceMarketOptions(
        LaunchTemplateInstanceMarketOptions.builder()
          .marketType("spot")
          .spotOptions(LaunchTemplateSpotMarketOptions.builder().maxPrice("0.5").build())
          .build())
    }
    def ltVersionBeforeModify = LaunchTemplateVersion.builder()
      .launchTemplateName(LT_ID_1)
      .versionNumber(Long.valueOf(LT_ID_1_V))
      .launchTemplateData(ltVersionDataBuilder.build())
      .build()

    when:
    SagaAction.Result result = prepareAction.apply(prepareCommand, new Saga("test", "test"))

    then:
    1 * asgService.getAutoScalingGroup("test-v001") >> autoScalingGroup
    1 * ltService.getLaunchTemplateVersion(autoScalingGroup.launchTemplate) >> Optional.of(ltVersionBeforeModify)

    and:
    def nextCommand = ((ModifyServerGroupLaunchTemplate.ModifyServerGroupLaunchTemplateCommand) result.nextCommand)
    nextCommand instanceof ModifyServerGroupLaunchTemplate.ModifyServerGroupLaunchTemplateCommand
    nextCommand.isReqToModifyLaunchTemplate == !expectedToSkipStep
    nextCommand.isAsgBackedByMixedInstancesPolicy == false
    nextCommand.isReqToUpgradeAsgToMixedInstancesPolicy == true

    and:
    def descPassed = nextCommand.description
    descPassed.spotPrice == expectedSpotPrice

    where:
    spotAllocationStrategy | newSpotPrice| asgHasSpotLt ||  expectedSpotPrice  || expectedToSkipStep
     "capacity-optimized"  |   "1"       |    true      ||         "1"         ||    false             // modify LT, create a new LT version with new spot max price
     "capacity-optimized"  |  ""         |    true      ||          null       ||    false             // modify LT, create a new LT version withOUT spot options
     "capacity-optimized"  |  null       |    true      ||         "0.5"       ||    false             // modify LT, create a new LT version with new spot max price
                null       |  "1"        |    false     ||         "1"         ||    true              // skip new LT version, and upgrade to MIP
     "capacity-optimized"  |  null       |    false     ||          null       ||    true              // skip new LT version, and upgrade to MIP

  }

  @Unroll
  def "should resolve image id correctly, with precedence give to imageId first, ami name second"() {
    given:
    modifyDescription.imageId = imageIdInReq
    modifyDescription.amiName = amiNameInReq

    def ltVersionBeforeModify = LaunchTemplateVersion.builder()
      .launchTemplateName(LT_ID_1)
      .versionNumber(1L)
      .launchTemplateData(ResponseLaunchTemplateData.builder().imageId(imageIdInSrc).build())
      .build()

    when:
    prepareAction.apply(prepareCommand, new Saga("test", "test"))

    then:
    1 * asgService.getAutoScalingGroup(autoScalingGroupWithLt.autoScalingGroupName) >> autoScalingGroupWithLt
    1 * ltService.getLaunchTemplateVersion(autoScalingGroupWithLt.launchTemplate) >> Optional.of(ltVersionBeforeModify)
    resolveAmiCallCount * ec2.describeImages(_) >> { DescribeImagesRequest req ->
      DescribeImagesResponse.builder().images(req.imageIds.collect { Image.builder().imageId("img-from-ami").build() }).build()
    }

    where:
    imageIdInReq | amiNameInReq | resolveAmiCallCount | imageIdInSrc   || expectedImageIdPassed
    "img-req"    | "ami-1"      |         0           | "img-src"      ||   "img-req"
    null         | "ami-1"      |         1           |  "img-src"     ||   "img-from-ami"
    null         | null         |         0           |  "img-src"     ||   "img-src"
  }

  @Unroll
  def "should include security groups from previous launch template: #desc"() {
    given:
    modifyDescription.securityGroups = securityGroups
    modifyDescription.securityGroupsAppendOnly = sgAppendOnly
    modifyDescription.amiName = "ami-1"

    def launchTemplateData = ResponseLaunchTemplateData.builder()
      .imageId("ami-1")
      .networkInterfaces([
        LaunchTemplateInstanceNetworkInterfaceSpecification.builder()
          .deviceIndex(0)
          .groups(["sg-1"])
          .build()
      ])
      .build()

    def ltVersionBeforeModify = LaunchTemplateVersion.builder()
      .launchTemplateName(LT_ID_1)
      .versionNumber(1L)
      .launchTemplateData(launchTemplateData)
      .build()

    when:
    prepareAction.apply(prepareCommand, new Saga("test", "test"))

    then:
    1 * asgService.getAutoScalingGroup(autoScalingGroupWithLt.autoScalingGroupName) >> autoScalingGroupWithLt
    1 * ltService.getLaunchTemplateVersion(autoScalingGroupWithLt.launchTemplate) >> Optional.of(ltVersionBeforeModify)
    1 * ec2.describeImages(_) >> { DescribeImagesRequest req ->
      DescribeImagesResponse.builder().images(req.imageIds.collect { Image.builder().imageId(it).build() }).build()
    }
    modifyDescription.getSecurityGroups().sort() == expectedGroups

    where:
    securityGroups | sgAppendOnly || expectedGroups   || desc
    null           | null          | ["sg-1"]         || "No specified groups and no specified appendOnly includes existing groups"
    null           | false         | []               || "With appendOnly explicitly false, clear groups if non supplied"
    null           | true          | ["sg-1"]         || "With appendOnly true, always add existing groups"
    ["sg-2"]       | null          | ["sg-2"]         || "With no specified appendOnly but provided groups, only use provided groups"
    ["sg-2"]       | false         | ["sg-2"]         || "With appendOnly false, use the specified groups only"
    ["sg-2"]       | true          | ["sg-1", "sg-2"] || "With appendOnly true, merge provided and existing groups"
  }

  def "should reset custom block devices when changing instance type"() {
    given:
    String newInstanceType = "m3-large"
    modifyDescription.amiName = "ami-1"
    modifyDescription.imageId = "ami-1"
    modifyDescription.instanceType = newInstanceType
    modifyDescription.blockDevices = null

    def launchTemplateData = ResponseLaunchTemplateData.builder()
      .imageId("ami-1")
      .instanceType("m3-medium")
      .networkInterfaces([
        LaunchTemplateInstanceNetworkInterfaceSpecification.builder()
          .deviceIndex(0)
          .groups(["sg-1"])
          .build()
      ])
      .blockDeviceMappings([
        LaunchTemplateBlockDeviceMapping.builder()
          .deviceName("/dev/sdb")
          .ebs(LaunchTemplateEbsBlockDevice.builder().volumeSize(40).build())
          .build()
      ])
      .build()

    def ltVersionBeforeModify = LaunchTemplateVersion.builder()
      .launchTemplateName(LT_ID_1)
      .versionNumber(1L)
      .launchTemplateData(launchTemplateData)
      .build()

    when:
    prepareAction.apply(prepareCommand, new Saga("test", "test"))

    then:
    1 * asgService.getAutoScalingGroup("test-v001") >> autoScalingGroupWithLt
    1 * ltService.getLaunchTemplateVersion(autoScalingGroupWithLt.launchTemplate) >> Optional.of(ltVersionBeforeModify)
    1 * blockDeviceConfig.getBlockDevicesForInstanceType(launchTemplateData.instanceType) >> [
      new AmazonBlockDevice(deviceName: '/dev/sdb', size: 40)
    ]
    1 * blockDeviceConfig.getBlockDevicesForInstanceType(newInstanceType) >> [
      new AmazonBlockDevice(deviceName: '/dev/sdb', size: 80)
    ]

    modifyDescription.blockDevices.size() == 1
    modifyDescription.blockDevices[0].size == 80
  }
}
