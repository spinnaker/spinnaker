/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DisableMetricsCollectionRequest
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.DescribeImagesResult
import com.amazonaws.services.ec2.model.DescribeVpcClassicLinkResult
import com.amazonaws.services.ec2.model.Image
import com.netflix.spinnaker.config.AwsConfiguration
import com.netflix.spinnaker.clouddriver.aws.deploy.BlockDeviceConfig
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.amazonaws.services.ec2.model.VpcClassicLink
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.LaunchConfigurationBuilder
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ModifyAsgLaunchConfigurationDescription
import com.netflix.spinnaker.clouddriver.aws.services.AsgService
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class ModifyAsgLaunchConfigurationOperationSpec extends Specification {
  def lcBuilder = Mock(LaunchConfigurationBuilder)
  def autoScaling = Mock(AmazonAutoScaling)
  def asgService = Mock(AsgService)

  def description = new ModifyAsgLaunchConfigurationDescription()

  @Shared
  def defaults = new AwsConfiguration.DeployDefaults(classicLinkSecurityGroupName: 'nf-classiclink')

  @Shared
  def blockDeviceConfig = new BlockDeviceConfig(defaults)

  @Subject op = new ModifyAsgLaunchConfigurationOperation(description)

  void setup() {
    def task = Stub(Task)
    op.deployDefaults = defaults
    TaskRepository.threadLocalTask.set(task)

    def amazonEC2 = Stub(AmazonEC2) {
      describeImages(_) >> { DescribeImagesRequest req ->
        new DescribeImagesResult().withImages(req.imageIds.collect { new Image(imageId: it)})
      }
      describeVpcClassicLink() >> {
        new DescribeVpcClassicLinkResult(vpcs: [
                new VpcClassicLink(vpcId: "vpc-123", classicLinkEnabled: false),
                new VpcClassicLink(vpcId: "vpc-456", classicLinkEnabled: true),
        ])
      }
    }

    def regionScopedProvider = Stub(RegionScopedProviderFactory.RegionScopedProvider) {
      getAmazonEC2() >> amazonEC2
      getAutoScaling() >> autoScaling
      getLaunchConfigurationBuilder() >> lcBuilder
      getAsgService() >> asgService
    }

    def regionScopedProviderFactory = Stub(RegionScopedProviderFactory) {
      forRegion(_, _) >> regionScopedProvider
    }

    op.regionScopedProviderFactory = regionScopedProviderFactory

    op.blockDeviceConfig = blockDeviceConfig
  }

  void 'should not modify launch configuration if no changes would result'() {
    setup:
    def credential = TestCredential.named(account)
    description.credentials = credential
    description.region = region
    description.asgName = asgName
    description.amiName = existingAmi
    description.iamRole = iamRole

    when:
    op.operate([])

    then:
    1 * asgService.getAutoScalingGroup(asgName) >> new AutoScalingGroup().withLaunchConfigurationName(existingLc)
    1 * lcBuilder.buildSettingsFromLaunchConfiguration(_, _, _) >> { act, region, name ->
      assert act == credential
      assert region == region
      assert name == existingLc

      existing
    }
    0 * _

    where:
    account = 'test'
    app = 'foo'
    region = 'us-east-1'
    asgName = "$app-v001".toString()
    suffix = '20150515'
    existingLc = "$asgName-$suffix".toString()
    newLc = "$asgName-20150516".toString()
    existingAmi = 'ami-f000fee'
    iamRole = 'BaseIAMRole'
    existing = new LaunchConfigurationBuilder.LaunchConfigurationSettings(
      account: account,
      environment: 'test',
      accountType: 'test',
      classicLinkVpcId: 'vpc-456',
      region: region,
      baseName: asgName,
      suffix: suffix,
      ami: existingAmi,
      iamRole: iamRole,
      instanceType: 'm3.xlarge',
      keyPair: 'sekret',
      associatePublicIpAddress: false,
      ebsOptimized: true,
      securityGroups: ['sg-12345', 'sg-34567']
    )
  }

  void 'should apply description fields over existing settings'() {
    setup:
    def credential = TestCredential.named(account)
    description.credentials = credential
    description.region = region
    description.asgName = asgName
    description.amiName = newAmi

    when:
    op.operate([])

    then:
    1 * asgService.getAutoScalingGroup(asgName) >> new AutoScalingGroup().withLaunchConfigurationName(existingLc)
    1 * lcBuilder.buildSettingsFromLaunchConfiguration(_, _, _) >> { act, region, name ->
      assert act == credential
      assert region == region
      assert name == existingLc

      existing
    }

    1 * lcBuilder.buildLaunchConfiguration(_, _, _, _) >> { appName, subnetType, settings, legacyUdf ->
      assert appName == app
      assert subnetType == null
      assert settings.ami == newAmi
      assert settings.iamRole == existing.iamRole
      assert settings.suffix == null
      assert legacyUdf == null

      return newLc
    }
    1 * autoScaling.updateAutoScalingGroup(_) >> { UpdateAutoScalingGroupRequest req ->
      assert req.autoScalingGroupName == asgName
      assert req.launchConfigurationName == newLc
    }

    0 * _

    where:
    account = 'test'
    app = 'foo'
    region = 'us-east-1'
    asgName = "$app-v001".toString()
    suffix = '20150515'
    existingLc = "$asgName-$suffix".toString()
    newLc = "$asgName-20150516".toString()
    newAmi = 'ami-f000fee'
    existing = new LaunchConfigurationBuilder.LaunchConfigurationSettings(
      account: account,
      environment: 'test',
      accountType: 'test',
      region: region,
      baseName: asgName,
      suffix: suffix,
      ami: 'ami-f111f333',
      iamRole: 'BaseIAMRole',
      instanceType: 'm3.xlarge',
      keyPair: 'sekret',
      associatePublicIpAddress: false,
      ebsOptimized: true,
      securityGroups: ['sg-12345', 'sg-34567']
    )
  }

  void 'should disable monitoring if instance monitoring goes from enabled to disabled'() {
    setup:
    def credential = TestCredential.named(account)
    description.credentials = credential
    description.region = region
    description.asgName = asgName
    description.instanceMonitoring = false

    when:
    op.operate([])

    then:
    1 * asgService.getAutoScalingGroup(asgName) >> new AutoScalingGroup().withLaunchConfigurationName(existingLc)
    1 * lcBuilder.buildSettingsFromLaunchConfiguration(_, _, _) >> { act, region, name ->
      assert act == credential
      assert region == region
      assert name == existingLc

      existing
    }
    1 * lcBuilder.buildLaunchConfiguration(_, _, _, _) >> { appName, subnetType, settings, legacyUdf ->
      assert appName == app
      assert subnetType == null
      assert settings.suffix == null
      assert settings.instanceMonitoring == false
      assert legacyUdf == null

      return newLc
    }
    1 * autoScaling.disableMetricsCollection(_) >> { DisableMetricsCollectionRequest req ->
      assert req.autoScalingGroupName == asgName
    }
    1 * autoScaling.updateAutoScalingGroup(_) >> { UpdateAutoScalingGroupRequest req ->
      assert req.autoScalingGroupName == asgName
      assert req.launchConfigurationName == newLc
    }

    where:
    account = 'test'
    app = 'foo'
    region = 'us-east-1'
    asgName = "$app-v001".toString()
    suffix = '20150515'
    existingLc = "$asgName-$suffix".toString()
    newLc = "$asgName-20150516".toString()
    existingAmi = 'ami-f000fee'
    iamRole = 'BaseIAMRole'
    existing = new LaunchConfigurationBuilder.LaunchConfigurationSettings(
      account: account,
      environment: 'test',
      accountType: 'test',
      region: region,
      baseName: asgName,
      suffix: suffix,
      instanceMonitoring: true,
    )
  }

  void 'should attach classic linked VPC'() {
    setup:
    def credential = TestCredential.named(account)
    description.credentials = credential
    description.region = region
    description.asgName = asgName
    description.instanceMonitoring = false

    when:
    op.operate([])

    then:
    1 * asgService.getAutoScalingGroup(asgName) >> new AutoScalingGroup().withLaunchConfigurationName(existingLc)
    1 * lcBuilder.buildSettingsFromLaunchConfiguration(_, _, _) >> { act, region, name ->
      assert act == credential
      assert region == region
      assert name == existingLc

      existing
    }
    1 * lcBuilder.buildLaunchConfiguration(_, _, _, _) >> { appName, subnetType, settings, legacyUdf ->
      assert appName == app
      assert subnetType == null
      assert settings.suffix == null
      assert settings.classicLinkVpcId == "vpc-456"
      assert legacyUdf == null

      return newLc
    }

    where:
    account = 'test'
    app = 'foo'
    region = 'us-east-1'
    asgName = "$app-v001".toString()
    suffix = '20150515'
    existingLc = "$asgName-$suffix".toString()
    newLc = "$asgName-20150516".toString()
    existingAmi = 'ami-f000fee'
    iamRole = 'BaseIAMRole'
    existing = new LaunchConfigurationBuilder.LaunchConfigurationSettings(
      account: account,
      environment: 'test',
      accountType: 'test',
      region: region,
      baseName: asgName,
      suffix: suffix,
      instanceMonitoring: true,
    )
  }

  void 'should append security groups if flag is set'() {
    setup:
    def credential = TestCredential.named(account)
    description.credentials = credential
    description.region = region
    description.asgName = asgName
    description.securityGroups = ['sg-3']
    description.securityGroupsAppendOnly = true

    when:
    op.operate([])

    then:
    1 * asgService.getAutoScalingGroup(asgName) >> new AutoScalingGroup().withLaunchConfigurationName(existingLc)
    1 * lcBuilder.buildSettingsFromLaunchConfiguration(_, _, _) >> { act, region, name ->
      assert act == credential
      assert region == region
      assert name == existingLc

      existing
    }
    1 * lcBuilder.buildLaunchConfiguration(_, _, _, _) >> { appName, subnetType, settings, legacyUdf ->
      assert settings.securityGroups == ['sg-1', 'sg-2', 'sg-3']
      return newLc
    }

    where:
    account = 'test'
    app = 'foo'
    region = 'us-east-1'
    asgName = "$app-v001".toString()
    suffix = '20150515'
    existingLc = "$asgName-$suffix".toString()
    newLc = "$asgName-20150516".toString()
    existingAmi = 'ami-f000fee'
    iamRole = 'BaseIAMRole'
    existing = new LaunchConfigurationBuilder.LaunchConfigurationSettings(
      account: account,
      environment: 'test',
      accountType: 'test',
      region: region,
      baseName: asgName,
      suffix: suffix,
      instanceMonitoring: true,
      securityGroups: ['sg-1', 'sg-2']
    )
  }

  void 'should reset non customized block devices when changing instance type'() {
    setup:
    def credential = TestCredential.named(account)
    description.credentials = credential
    description.region = region
    description.asgName = asgName
    description.instanceType = 'm4.xlarge'

    when:
    op.operate([])

    then:
    1 * asgService.getAutoScalingGroup(asgName) >> new AutoScalingGroup().withLaunchConfigurationName(existingLc)
    1 * lcBuilder.buildSettingsFromLaunchConfiguration(_, _, _) >> { act, region, name ->
      assert act == credential
      assert region == region
      assert name == existingLc

      existing
    }

    1 * lcBuilder.buildLaunchConfiguration(_, _, _, _) >> { appName, subnetType, settings, legacyUdf ->
      assert appName == app
      assert subnetType == null
      assert settings.iamRole == existing.iamRole
      assert settings.suffix == null
      assert legacyUdf == null
      assert settings.instanceType == 'm4.xlarge'
      assert settings.blockDevices == blockDeviceConfig.getBlockDevicesForInstanceType('m4.xlarge')

      return newLc
    }
    1 * autoScaling.updateAutoScalingGroup(_) >> { UpdateAutoScalingGroupRequest req ->
      assert req.autoScalingGroupName == asgName
      assert req.launchConfigurationName == newLc
    }

    0 * _

    where:
    account = 'test'
    app = 'foo'
    region = 'us-east-1'
    asgName = "$app-v001".toString()
    suffix = '20150515'
    existingLc = "$asgName-$suffix".toString()
    newLc = "$asgName-20150516".toString()
    existing = new LaunchConfigurationBuilder.LaunchConfigurationSettings(
        account: account,
        environment: 'test',
        accountType: 'test',
        region: region,
        baseName: asgName,
        suffix: suffix,
        ami: 'ami-f111f333',
        iamRole: 'BaseIAMRole',
        instanceType: 'm3.xlarge',
        blockDevices: blockDeviceConfig.getBlockDevicesForInstanceType('m3.xlarge'),
        keyPair: 'sekret',
        associatePublicIpAddress: false,
        ebsOptimized: true,
        securityGroups: ['sg-12345', 'sg-34567']
    )
  }

  void 'should not reset custom block devices when changing instance type'() {
    setup:
    def credential = TestCredential.named(account)
    description.credentials = credential
    description.region = region
    description.asgName = asgName
    description.instanceType = 'm4.xlarge'

    when:
    op.operate([])

    then:
    1 * asgService.getAutoScalingGroup(asgName) >> new AutoScalingGroup().withLaunchConfigurationName(existingLc)
    1 * lcBuilder.buildSettingsFromLaunchConfiguration(_, _, _) >> { act, region, name ->
      assert act == credential
      assert region == region
      assert name == existingLc

      existing
    }

    1 * lcBuilder.buildLaunchConfiguration(_, _, _, _) >> { appName, subnetType, settings, legacyUdf ->
      assert appName == app
      assert subnetType == null
      assert settings.iamRole == existing.iamRole
      assert settings.suffix == null
      assert legacyUdf == null
      assert settings.instanceType == 'm4.xlarge'
      assert settings.blockDevices == blockDevices

      return newLc
    }
    1 * autoScaling.updateAutoScalingGroup(_) >> { UpdateAutoScalingGroupRequest req ->
      assert req.autoScalingGroupName == asgName
      assert req.launchConfigurationName == newLc
    }

    0 * _

    where:
    account = 'test'
    app = 'foo'
    region = 'us-east-1'
    asgName = "$app-v001".toString()
    suffix = '20150515'
    existingLc = "$asgName-$suffix".toString()
    newLc = "$asgName-20150516".toString()
    blockDevices = [new AmazonBlockDevice(deviceName: '/dev/sdb', size: 500)]
    existing = new LaunchConfigurationBuilder.LaunchConfigurationSettings(
        account: account,
        environment: 'test',
        accountType: 'test',
        region: region,
        baseName: asgName,
        suffix: suffix,
        ami: 'ami-f111f333',
        iamRole: 'BaseIAMRole',
        instanceType: 'm3.xlarge',
        blockDevices: blockDevices,
        keyPair: 'sekret',
        associatePublicIpAddress: false,
        ebsOptimized: true,
        securityGroups: ['sg-12345', 'sg-34567']
    )
  }

  void 'should reset custom block devices when changing instance type if explicitly requested'() {
    setup:
    def credential = TestCredential.named(account)
    description.credentials = credential
    description.region = region
    description.asgName = asgName
    description.instanceType = 'm4.xlarge'
    description.copySourceCustomBlockDeviceMappings = false

    when:
    op.operate([])

    then:
    1 * asgService.getAutoScalingGroup(asgName) >> new AutoScalingGroup().withLaunchConfigurationName(existingLc)
    1 * lcBuilder.buildSettingsFromLaunchConfiguration(_, _, _) >> { act, region, name ->
      assert act == credential
      assert region == region
      assert name == existingLc

      existing
    }

    1 * lcBuilder.buildLaunchConfiguration(_, _, _, _) >> { appName, subnetType, settings, legacyUdf ->
      assert appName == app
      assert subnetType == null
      assert settings.iamRole == existing.iamRole
      assert settings.suffix == null
      assert legacyUdf == null
      assert settings.instanceType == 'm4.xlarge'
      assert settings.blockDevices == blockDeviceConfig.getBlockDevicesForInstanceType('m4.xlarge')

      return newLc
    }
    1 * autoScaling.updateAutoScalingGroup(_) >> { UpdateAutoScalingGroupRequest req ->
      assert req.autoScalingGroupName == asgName
      assert req.launchConfigurationName == newLc
    }

    0 * _

    where:
    account = 'test'
    app = 'foo'
    region = 'us-east-1'
    asgName = "$app-v001".toString()
    suffix = '20150515'
    existingLc = "$asgName-$suffix".toString()
    newLc = "$asgName-20150516".toString()
    blockDevices = [new AmazonBlockDevice(deviceName: '/dev/sdb', size: 500)]
    existing = new LaunchConfigurationBuilder.LaunchConfigurationSettings(
        account: account,
        environment: 'test',
        accountType: 'test',
        region: region,
        baseName: asgName,
        suffix: suffix,
        ami: 'ami-f111f333',
        iamRole: 'BaseIAMRole',
        instanceType: 'm3.xlarge',
        blockDevices: blockDevices,
        keyPair: 'sekret',
        associatePublicIpAddress: false,
        ebsOptimized: true,
        securityGroups: ['sg-12345', 'sg-34567']
    )
  }


}
