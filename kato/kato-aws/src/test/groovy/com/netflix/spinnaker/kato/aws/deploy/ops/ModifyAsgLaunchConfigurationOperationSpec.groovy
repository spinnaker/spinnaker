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

package com.netflix.spinnaker.kato.aws.deploy.ops

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DisableMetricsCollectionRequest
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.DescribeImagesResult
import com.amazonaws.services.ec2.model.Image
import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.aws.deploy.LaunchConfigurationBuilder
import com.netflix.spinnaker.kato.aws.deploy.description.ModifyAsgLaunchConfigurationDescription
import com.netflix.spinnaker.kato.aws.services.AsgService
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import spock.lang.Specification
import spock.lang.Subject

class ModifyAsgLaunchConfigurationOperationSpec extends Specification {
  def lcBuilder = Mock(LaunchConfigurationBuilder)
  def autoScaling = Mock(AmazonAutoScaling)
  def asgService = Mock(AsgService)

  def description = new ModifyAsgLaunchConfigurationDescription()

  @Subject op = new ModifyAsgLaunchConfigurationOperation(description)

  void setup() {
    def task = Stub(Task)
    TaskRepository.threadLocalTask.set(task)

    def amazonEC2 = Stub(AmazonEC2) {
      describeImages(_) >> { DescribeImagesRequest req ->
        new DescribeImagesResult().withImages(req.imageIds.collect { new Image(imageId: it)})
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
  }

  void 'should not modify launch configuration if no changes would result'() {
    setup:
    description.credentials = TestCredential.named(account)
    description.region = region
    description.asgName = asgName
    description.amiName = existingAmi
    description.iamRole = iamRole

    when:
    op.operate([])

    then:
    1 * asgService.getAutoScalingGroup(asgName) >> new AutoScalingGroup().withLaunchConfigurationName(existingLc)
    1 * lcBuilder.buildSettingsFromLaunchConfiguration(_, _, _) >> { act, region, name ->
      assert act == account
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
    description.credentials = TestCredential.named(account)
    description.region = region
    description.asgName = asgName
    description.amiName = newAmi

    when:
    op.operate([])

    then:
    1 * asgService.getAutoScalingGroup(asgName) >> new AutoScalingGroup().withLaunchConfigurationName(existingLc)
    1 * lcBuilder.buildSettingsFromLaunchConfiguration(_, _, _) >> { act, region, name ->
      assert act == account
      assert region == region
      assert name == existingLc

      existing
    }

    1 * lcBuilder.buildLaunchConfiguration(_, _, _) >> { appName, subnetType, settings ->
      assert appName == app
      assert subnetType == null
      assert settings.ami == newAmi
      assert settings.iamRole == existing.iamRole
      assert settings.suffix == null

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
    description.credentials = TestCredential.named(account)
    description.region = region
    description.asgName = asgName
    description.instanceMonitoring = false

    when:
    op.operate([])

    then:
    1 * asgService.getAutoScalingGroup(asgName) >> new AutoScalingGroup().withLaunchConfigurationName(existingLc)
    1 * lcBuilder.buildSettingsFromLaunchConfiguration(_, _, _) >> { act, region, name ->
      assert act == account
      assert region == region
      assert name == existingLc

      existing
    }
    1 * lcBuilder.buildLaunchConfiguration(_, _, _) >> { appName, subnetType, settings ->
      assert appName == app
      assert subnetType == null
      assert settings.suffix == null
      assert settings.instanceMonitoring == false

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
      region: region,
      baseName: asgName,
      suffix: suffix,
      instanceMonitoring: true,
    )
  }

}
