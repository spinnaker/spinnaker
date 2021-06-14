package com.netflix.spinnaker.clouddriver.aws.deploy.ops.actions

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.InstancesDistribution
import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification
import com.amazonaws.services.autoscaling.model.MixedInstancesPolicy
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.*
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
  def ec2 = Mock(AmazonEC2)
  def blockDeviceConfig = Mock(BlockDeviceConfig)
  def credentialsRepository = Stub(MapBackedCredentialsRepository) {
    getOne(_) >> credentials
  }

  def autoScalingGroupWithLt = new AutoScalingGroup(
    autoScalingGroupName: "test-v001",
    launchTemplate: new LaunchTemplateSpecification(launchTemplateName: LT_ID_1, version: LT_ID_1_V)
  )

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
    def ltVersionBeforeModify = new LaunchTemplateVersion(
      launchTemplateName: LT_ID_1,
      versionNumber: LT_ID_1_V,
      launchTemplateData: new ResponseLaunchTemplateData(
        imageId: "ami-1",
        instanceType: "c3.large"
      )
    )

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
    def mixedInstancesPolicy = new MixedInstancesPolicy(
      launchTemplate: new com.amazonaws.services.autoscaling.model.LaunchTemplate(
        launchTemplateSpecification: autoScalingGroupWithLt.launchTemplate,
        overrides: [
          new com.amazonaws.services.autoscaling.model.LaunchTemplateOverrides(instanceType: "c3.large", weightedCapacity: "2"),
          new com.amazonaws.services.autoscaling.model.LaunchTemplateOverrides(instanceType: "c3.xlarge", weightedCapacity: "4")
        ]
      ),
      instancesDistribution: new InstancesDistribution(
        onDemandAllocationStrategy: "prioritized",
        onDemandBaseCapacity: 2,
        onDemandPercentageAboveBaseCapacity: 50,
        spotAllocationStrategy: "lowest-price",
        spotInstancePools: 2,
        spotMaxPrice: "1"
      )
    )
    def autoScalingGroup = new AutoScalingGroup(
      autoScalingGroupName: "test-v001",
      mixedInstancesPolicy: mixedInstancesPolicy
    )

    def ltVersionBeforeModify = new LaunchTemplateVersion(
      launchTemplateName: LT_ID_1,
      versionNumber: LT_ID_1_V,
      launchTemplateData: new ResponseLaunchTemplateData(
        imageId: "ami-1",
        instanceType: "m5.large"
      )
    )

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
    nextCommand.description.onDemandAllocationStrategy == mixedInstancesPolicy.getInstancesDistribution().getOnDemandAllocationStrategy()
    nextCommand.description.onDemandBaseCapacity == mixedInstancesPolicy.getInstancesDistribution().getOnDemandBaseCapacity()
    nextCommand.description.onDemandPercentageAboveBaseCapacity == mixedInstancesPolicy.getInstancesDistribution().getOnDemandPercentageAboveBaseCapacity()

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
    def autoScalingGroup = new AutoScalingGroup(
      autoScalingGroupName: "test-v001",
      launchTemplate: new LaunchTemplateSpecification(launchTemplateName: LT_ID_1, version: LT_ID_1_V)
    )

    def ltVersionBeforeModify = new LaunchTemplateVersion(
      launchTemplateName: LT_ID_1,
      versionNumber: LT_ID_1_V,
      launchTemplateData: new ResponseLaunchTemplateData(
        imageId: "ami-1",
        instanceMarketOptions: asgHasSpotLt
          ? new LaunchTemplateInstanceMarketOptions().withMarketType("spot").withSpotOptions(new LaunchTemplateSpotMarketOptions(maxPrice: "0.5"))
          : null
      )
    )

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

    def ltVersionBeforeModify = new LaunchTemplateVersion(
      launchTemplateName: LT_ID_1,
      versionNumber: 1,
      launchTemplateData: new ResponseLaunchTemplateData(
        imageId: imageIdInSrc
      )
    )

    when:
    prepareAction.apply(prepareCommand, new Saga("test", "test"))

    then:
    1 * asgService.getAutoScalingGroup(autoScalingGroupWithLt.autoScalingGroupName) >> autoScalingGroupWithLt
    1 * ltService.getLaunchTemplateVersion(autoScalingGroupWithLt.launchTemplate) >> Optional.of(ltVersionBeforeModify)
    resolveAmiCallCount * ec2.describeImages(_) >> { DescribeImagesRequest req ->
      new DescribeImagesResult().withImages(req.imageIds.collect { new Image(imageId: "img-from-ami") })
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

    def launchTemplateData = new ResponseLaunchTemplateData(
      imageId: "ami-1",
      networkInterfaces: [
        new LaunchTemplateInstanceNetworkInterfaceSpecification(
          deviceIndex: 0,
          groups: ["sg-1"]
        )
      ],
    )

    def ltVersionBeforeModify = new LaunchTemplateVersion(
      launchTemplateName: LT_ID_1,
      versionNumber: 1,
      launchTemplateData: launchTemplateData
    )

    when:
    prepareAction.apply(prepareCommand, new Saga("test", "test"))

    then:
    1 * asgService.getAutoScalingGroup(autoScalingGroupWithLt.autoScalingGroupName) >> autoScalingGroupWithLt
    1 * ltService.getLaunchTemplateVersion(autoScalingGroupWithLt.launchTemplate) >> Optional.of(ltVersionBeforeModify)
    1 * ec2.describeImages(_) >> { DescribeImagesRequest req ->
      new DescribeImagesResult().withImages(req.imageIds.collect { new Image(imageId: it) })
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

    def launchTemplateData = new ResponseLaunchTemplateData(
      imageId: "ami-1",
      instanceType: "m3-medium",
      networkInterfaces: [
        new LaunchTemplateInstanceNetworkInterfaceSpecification(
          deviceIndex: "0",
          groups: ["sg-1"]
        )
      ],
      blockDeviceMappings: [
        new LaunchTemplateBlockDeviceMapping(
          deviceName: "/dev/sdb",
          ebs: new LaunchTemplateEbsBlockDevice(volumeSize: 40)
        )
      ]
    )

    def ltVersionBeforeModify = new LaunchTemplateVersion(
      launchTemplateName: LT_ID_1,
      versionNumber: 1,
      launchTemplateData: launchTemplateData
    )

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
