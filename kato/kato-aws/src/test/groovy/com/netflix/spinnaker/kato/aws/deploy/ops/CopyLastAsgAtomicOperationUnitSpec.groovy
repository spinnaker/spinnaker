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

package com.netflix.spinnaker.kato.aws.deploy.ops
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.BlockDeviceMapping
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsRequest
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsResult
import com.amazonaws.services.autoscaling.model.Ebs
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.amazonaws.services.ec2.AmazonEC2
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.aws.deploy.AsgReferenceCopier
import com.netflix.spinnaker.kato.aws.deploy.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.kato.aws.deploy.handlers.BasicAmazonDeployHandler
import com.netflix.spinnaker.kato.aws.services.AsgService
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.DeploymentResult
import spock.lang.Specification

class CopyLastAsgAtomicOperationUnitSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "operation builds description based on ancestor asg"() {
    setup:
    def deployHandler = Mock(BasicAmazonDeployHandler)
    def description = new BasicAmazonDeployDescription(application: "asgard", stack: "stack")
    description.availabilityZones = ['us-east-1': [], 'us-west-1': []]
    description.credentials = TestCredential.named('baz')
    description.securityGroups = ['someGroupName', 'sg-12345a']
    description.capacity = new BasicAmazonDeployDescription.Capacity(min: 1, max: 3, desired: 5)
    def mockEC2 = Mock(AmazonEC2)
    def mockAutoScaling = Mock(AmazonAutoScaling)
    def mockProvider = Mock(AmazonClientProvider)
    def asgService = new AsgService(mockAutoScaling)
    mockProvider.getAmazonEC2(_, _, true) >> mockEC2
    mockProvider.getAutoScaling(_, _, true) >> mockAutoScaling
    def op = new CopyLastAsgAtomicOperation(description)
    op.amazonClientProvider = mockProvider
    op.basicAmazonDeployHandler = deployHandler
    def mockAsgReferenceCopier = Mock(AsgReferenceCopier)
    op.regionScopedProviderFactory = Stub(RegionScopedProviderFactory) {
      forRegion(_, _) >> Stub(RegionScopedProviderFactory.RegionScopedProvider) {
        getAsgReferenceCopier(_, _) >> mockAsgReferenceCopier
        getAsgService() >> asgService
      }
    }
    def expectedDeployDescription = { region ->
      new BasicAmazonDeployDescription(application: 'asgard', stack: 'stack', keyPair: 'key-pair-name',
        securityGroups: ['someGroupName', 'sg-12345a'], availabilityZones: [(region): null],
        capacity: new BasicAmazonDeployDescription.Capacity(min: 1, max: 3, desired: 5),
        source: new BasicAmazonDeployDescription.Source(
          asgName: "asgard-stack-v000",
          account: 'baz',
          region: null
        ))
    }

    when:
    op.operate([])

    then:
    2 * mockAutoScaling.describeLaunchConfigurations(_) >> { DescribeLaunchConfigurationsRequest request ->
      assert request.launchConfigurationNames == ['foo']
      def mockLaunch = Mock(LaunchConfiguration)
      mockLaunch.getLaunchConfigurationName() >> "foo"
      mockLaunch.getKeyName() >> "key-pair-name"
      mockLaunch.getBlockDeviceMappings() >> [new BlockDeviceMapping().withDeviceName('/dev/sdb').withEbs(new Ebs().withVolumeSize(125)), new BlockDeviceMapping().withDeviceName('/dev/sdc').withVirtualName('ephemeral1')]
      new DescribeLaunchConfigurationsResult().withLaunchConfigurations([mockLaunch])
    }
    2 * mockAutoScaling.describeAutoScalingGroups(_) >> {
      def mockAsg = Mock(AutoScalingGroup)
      mockAsg.getAutoScalingGroupName() >> "asgard-stack-v000"
      mockAsg.getMinSize() >> 0
      mockAsg.getMaxSize() >> 2
      mockAsg.getDesiredCapacity() >> 4
      mockAsg.getLaunchConfigurationName() >> "foo"
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups([mockAsg])
    }
    1 * deployHandler.handle(expectedDeployDescription('us-east-1'), _) >> new DeploymentResult(serverGroupNameByRegion: ['us-east-1': 'asgard-stack-v001'])
    1 * deployHandler.handle(expectedDeployDescription('us-west-1'), _) >> new DeploymentResult(serverGroupNameByRegion: ['us-west-1': 'asgard-stack-v001'])
  }
}
