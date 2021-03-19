package com.netflix.spinnaker.clouddriver.aws.deploy.ops.actions

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.*
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.InstanceTypeUtils.BlockDeviceConfig
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ModifyServerGroupLaunchTemplateDescription
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.clouddriver.aws.services.AsgService
import com.netflix.spinnaker.clouddriver.aws.services.LaunchTemplateService
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository
import spock.lang.Specification
import spock.lang.Subject

class PrepareModifyServerGroupLaunchTemplateSpec extends Specification {
  def credentials = TestCredential.named("test")
  def ltService = Mock(LaunchTemplateService)
  def asgService = Mock(AsgService)
  def ec2 = Mock(AmazonEC2)
  def blockDeviceConfig = Mock(BlockDeviceConfig)
  def credentialsRepository = Stub(MapBackedCredentialsRepository) {
    getOne(_) >> credentials
  }

  def autoScalingGroup = new AutoScalingGroup(
    autoScalingGroupName: "test-v001",
    launchTemplate: new LaunchTemplateSpecification(launchTemplateName: "lt-1", version: "1")
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

  def "should prepare for launch template update"() {
    given:
    def description = new ModifyServerGroupLaunchTemplateDescription(
      region: "us-east-1",
      asgName: autoScalingGroup.autoScalingGroupName,
      amiName: "ami-1",
      credentials: credentials
    )

    def prepareCommand = new PrepareModifyServerGroupLaunchTemplate.PrepareModifyServerGroupLaunchTemplateCommand(description, null)

    def ltVersion = new LaunchTemplateVersion(
      launchTemplateName: "lt-1",
      versionNumber: "1",
      launchTemplateData: new ResponseLaunchTemplateData(
        imageId: "ami-1",
      )
    )

    when:
    prepareAction.apply(prepareCommand, new Saga("test", "test"))

    then:
    1 * asgService.getAutoScalingGroup("test-v001") >> autoScalingGroup
    1 * ltService.getLaunchTemplateVersion(autoScalingGroup.launchTemplate) >> Optional.of(ltVersion)
    1 * ec2.describeImages(_) >> { DescribeImagesRequest req ->
      new DescribeImagesResult().withImages(req.imageIds.collect { new Image(imageId: it) })
    }
  }

  def "should include security groups from previous launch template: #desc"() {
    def description = new ModifyServerGroupLaunchTemplateDescription(
      region: "us-east-1",
      asgName: autoScalingGroup.autoScalingGroupName,
      amiName: "ami-1",
      securityGroups: securityGroups,
      securityGroupsAppendOnly: sgAppendOnly
    )
    def prepareCommand = new PrepareModifyServerGroupLaunchTemplate.PrepareModifyServerGroupLaunchTemplateCommand(description, null)

    def launchTemplateData = new ResponseLaunchTemplateData(
      imageId: "ami-1",
      networkInterfaces: [
        new LaunchTemplateInstanceNetworkInterfaceSpecification(
          deviceIndex: 0,
          groups: ["sg-1"]
        )
      ],
    )

    def ltVersion = new LaunchTemplateVersion(
      launchTemplateName: "lt-1",
      versionNumber: 1,
      launchTemplateData: launchTemplateData
    )

    when:
    prepareAction.apply(prepareCommand, new Saga("test", "test"))

    then:
    1 * asgService.getAutoScalingGroup(autoScalingGroup.autoScalingGroupName) >> autoScalingGroup
    1 * ltService.getLaunchTemplateVersion(autoScalingGroup.launchTemplate) >> Optional.of(ltVersion)
    1 * ec2.describeImages(_) >> { DescribeImagesRequest req ->
      new DescribeImagesResult().withImages(req.imageIds.collect { new Image(imageId: it) })
    }
    description.getSecurityGroups().sort() == expectedGroups

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
    def description = new ModifyServerGroupLaunchTemplateDescription(
      region: "us-east-1",
      asgName: autoScalingGroup.autoScalingGroupName,
      amiName: "ami-1",
      imageId: "ami-1",
      credentials: credentials,
      instanceType: newInstanceType,
      blockDevices: null
    )

    def prepareCommand = new PrepareModifyServerGroupLaunchTemplate.PrepareModifyServerGroupLaunchTemplateCommand(description, null)

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

    def ltVersion = new LaunchTemplateVersion(
      launchTemplateName: "lt-1",
      versionNumber: 1,
      launchTemplateData: launchTemplateData
    )

    when:
    prepareAction.apply(prepareCommand, new Saga("test", "test"))

    then:
    1 * asgService.getAutoScalingGroup("test-v001") >> autoScalingGroup
    1 * ltService.getLaunchTemplateVersion(autoScalingGroup.launchTemplate) >> Optional.of(ltVersion)
    1 * blockDeviceConfig.getBlockDevicesForInstanceType(launchTemplateData.instanceType) >> [
      new AmazonBlockDevice(deviceName: '/dev/sdb', size: 40)
    ]
    1 * blockDeviceConfig.getBlockDevicesForInstanceType(newInstanceType) >> [
      new AmazonBlockDevice(deviceName: '/dev/sdb', size: 80)
    ]

    description.blockDevices.size() == 1
    description.blockDevices[0].size == 80
  }
}
