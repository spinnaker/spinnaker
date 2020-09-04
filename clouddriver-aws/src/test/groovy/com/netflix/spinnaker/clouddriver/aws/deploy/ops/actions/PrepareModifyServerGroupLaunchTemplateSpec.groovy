package com.netflix.spinnaker.clouddriver.aws.deploy.ops.actions

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.Image
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.DescribeImagesResult
import com.amazonaws.services.ec2.model.LaunchTemplateBlockDeviceMapping
import com.amazonaws.services.ec2.model.LaunchTemplateEbsBlockDevice
import com.amazonaws.services.ec2.model.LaunchTemplateInstanceNetworkInterfaceSpecification
import com.amazonaws.services.ec2.model.LaunchTemplateVersion
import com.amazonaws.services.ec2.model.ResponseLaunchTemplateData
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.BlockDeviceConfig
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ModifyServerGroupLaunchTemplateDescription
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.clouddriver.aws.services.AsgService
import com.netflix.spinnaker.clouddriver.aws.services.LaunchTemplateService
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import spock.lang.Specification
import spock.lang.Subject

class PrepareModifyServerGroupLaunchTemplateSpec extends Specification {
  def credentials = TestCredential.named("test")
  def ltService = Mock(LaunchTemplateService)
  def asgService = Mock(AsgService)
  def ec2 = Mock(AmazonEC2)
  def blockDeviceConfig = Mock(BlockDeviceConfig)
  def credentialsRepository = Mock(AccountCredentialsRepository) {
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
  def prepareAction = new PrepareModifyServerGroupLaunchTemplate(blockDeviceConfig, credentialsRepository, regionScopedProviderFactory)

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
      new DescribeImagesResult().withImages(req.imageIds.collect { new Image(imageId: it)})
    }
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
