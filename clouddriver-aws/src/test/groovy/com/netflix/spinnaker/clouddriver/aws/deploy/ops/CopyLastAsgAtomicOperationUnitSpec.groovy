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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.BlockDeviceMapping
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsRequest
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsResult
import com.amazonaws.services.autoscaling.model.Ebs
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification
import com.amazonaws.services.autoscaling.model.TagDescription
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.CreditSpecification
import com.amazonaws.services.ec2.model.LaunchTemplateBlockDeviceMapping
import com.amazonaws.services.ec2.model.LaunchTemplateEbsBlockDevice
import com.amazonaws.services.ec2.model.LaunchTemplateInstanceMarketOptions
import com.amazonaws.services.ec2.model.LaunchTemplateSpotMarketOptions
import com.amazonaws.services.ec2.model.LaunchTemplateVersion
import com.amazonaws.services.ec2.model.ResponseLaunchTemplateData
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AWSServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.aws.deploy.validators.BasicAmazonDeployDescriptionValidator
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.services.AsgService
import com.netflix.spinnaker.clouddriver.aws.services.LaunchTemplateService
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AsgReferenceCopier
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.BasicAmazonDeployHandler
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import spock.lang.Specification
import spock.lang.Unroll

class CopyLastAsgAtomicOperationUnitSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  @Unroll
  void "operation builds description based on ancestor asg backed by a launch template"() {
    given:
    def launchTemplateVersion = new LaunchTemplateVersion(
      launchTemplateName: "foo",
      launchTemplateId: "foo",
      versionNumber: 0,
      launchTemplateData: new ResponseLaunchTemplateData(
        keyName: "key-pair-name",
        instanceMarketOptions: new LaunchTemplateInstanceMarketOptions(
          spotOptions: new LaunchTemplateSpotMarketOptions(
            maxPrice: ancestorSpotPrice
          )
        ),
        blockDeviceMappings: [
          new LaunchTemplateBlockDeviceMapping(
            deviceName: "/dev/sdb",
            ebs: new LaunchTemplateEbsBlockDevice(
              volumeSize: 125
            )
          ),
          new LaunchTemplateBlockDeviceMapping(
            deviceName: "/dev/sdc",
            virtualName: "ephemeral1"
          )
        ]
      )
    )

    def launchTemplateSpec = new LaunchTemplateSpecification(
      launchTemplateName: launchTemplateVersion.launchTemplateName,
      launchTemplateId: launchTemplateVersion.launchTemplateId,
      version: launchTemplateVersion.versionNumber.toString(),
    )

    def description = new BasicAmazonDeployDescription(
      application: "asgard",
      stack: "stack",
      availabilityZones: [
        'us-east-1': [],
        'us-west-1': []
      ],
      credentials: TestCredential.named('baz'),
      securityGroups: ["someGroupName", "sg-12345a"],
      capacity: new BasicAmazonDeployDescription.Capacity(min: 1, max: 3, desired: 5),
      spotPrice: requestSpotPrice
    )

    and:
    def deployHandler = Mock(BasicAmazonDeployHandler)
    def mockAutoScaling = Mock(AmazonAutoScaling)
    def ec2 = Mock(AmazonEC2)
    def mockProvider = Mock(AmazonClientProvider)
    mockProvider.getAmazonEC2(_, _, true) >> ec2
    mockProvider.getAutoScaling(_, _, true) >> mockAutoScaling

    def op = new CopyLastAsgAtomicOperation(description)
    op.amazonClientProvider = mockProvider
    op.basicAmazonDeployHandler = deployHandler

    def mockAsgReferenceCopier = Mock(AsgReferenceCopier)
    def asgService = new AsgService(mockAutoScaling)
    def serverGroupNameResolver = Mock(AWSServerGroupNameResolver)

    op.regionScopedProviderFactory = Stub(RegionScopedProviderFactory) {
      forRegion(_, _) >> Stub(RegionScopedProviderFactory.RegionScopedProvider) {
        getAsgReferenceCopier(_, _) >> mockAsgReferenceCopier
        getAsgService() >> asgService
        getAWSServerGroupNameResolver() >> serverGroupNameResolver
        getLaunchTemplateService() >> Mock(LaunchTemplateService) {
          getLaunchTemplateVersion(launchTemplateSpec) >> Optional.of(launchTemplateVersion)
        }
      }
    }

    op.basicAmazonDeployDescriptionValidator = Stub(BasicAmazonDeployDescriptionValidator)

    when:
    def result = op.operate([])

    then:
    result.serverGroupNameByRegion['us-east-1'] == 'asgard-stack-v001'
    result.serverGroupNameByRegion['us-west-1'] == 'asgard-stack-v001'
    result.serverGroupNames == ['asgard-stack-v001', 'asgard-stack-v001']

    2 * mockAutoScaling.describeAutoScalingGroups(_) >> {
      def mockAsg = Mock(AutoScalingGroup)
      mockAsg.getAutoScalingGroupName() >> "asgard-stack-v000"
      mockAsg.getMinSize() >> 0
      mockAsg.getMaxSize() >> 2
      mockAsg.getDesiredCapacity() >> 4
      mockAsg.getLaunchTemplate() >> launchTemplateSpec
      mockAsg.getTags() >> [new TagDescription().withKey('Name').withValue('name-tag')]
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups([mockAsg])
    }

    2 * serverGroupNameResolver.resolveLatestServerGroupName("asgard-stack") >> { "asgard-stack-v000" }
    0 * serverGroupNameResolver._
    1 * deployHandler.handle(expectedDescription(expectedSpotPrice, "us-east-1"), _) >>
      new DeploymentResult(serverGroupNames: ['asgard-stack-v001'], serverGroupNameByRegion: ['us-east-1': 'asgard-stack-v001'])
    1 * deployHandler.handle(expectedDescription(expectedSpotPrice, "us-west-1"), _) >>
      new DeploymentResult(serverGroupNames: ['asgard-stack-v001'], serverGroupNameByRegion: ['us-west-1': 'asgard-stack-v001'])

    where:
    requestSpotPrice | ancestorSpotPrice || expectedSpotPrice
    0.25             | null              || 0.25
    0.25             | 0.5               || 0.25
    null             | 0.25              || 0.25
    ""               | 0.25              || null
    null             | null              || null
  }

  @Unroll
  void "operation builds description based on ancestor asg backed by launch configuration"() {
    setup:
    def deployHandler = Mock(BasicAmazonDeployHandler)
    def description = new BasicAmazonDeployDescription(application: "asgard", stack: "stack")
    description.availabilityZones = ['us-east-1': [], 'us-west-1': []]
    description.credentials = TestCredential.named('baz')
    description.securityGroups = ['someGroupName', 'sg-12345a']
    description.capacity = new BasicAmazonDeployDescription.Capacity(min: 1, max: 3, desired: 5)
    description.spotPrice = requestSpotPrice
    def mockEC2 = Mock(AmazonEC2)
    def mockAutoScaling = Mock(AmazonAutoScaling)
    def mockProvider = Mock(AmazonClientProvider)
    mockProvider.getAmazonEC2(_, _, true) >> mockEC2
    mockProvider.getAutoScaling(_, _, true) >> mockAutoScaling
    def op = new CopyLastAsgAtomicOperation(description)
    op.amazonClientProvider = mockProvider
    op.basicAmazonDeployHandler = deployHandler

    def mockAsgReferenceCopier = Mock(AsgReferenceCopier)
    def asgService = new AsgService(mockAutoScaling)
    def serverGroupNameResolver = Mock(AWSServerGroupNameResolver)
    op.regionScopedProviderFactory = Stub(RegionScopedProviderFactory) {
      forRegion(_, _) >> Stub(RegionScopedProviderFactory.RegionScopedProvider) {
        getAsgReferenceCopier(_, _) >> mockAsgReferenceCopier
        getAsgService() >> asgService
        getAWSServerGroupNameResolver() >> serverGroupNameResolver
      }
    }
    op.basicAmazonDeployDescriptionValidator = Stub(BasicAmazonDeployDescriptionValidator)

    when:
    def result = op.operate([])

    then:
    result.serverGroupNameByRegion['us-east-1'] == 'asgard-stack-v001'
    result.serverGroupNameByRegion['us-west-1'] == 'asgard-stack-v001'
    result.serverGroupNames == ['asgard-stack-v001', 'asgard-stack-v001']
    2 * mockAutoScaling.describeLaunchConfigurations(_) >> { DescribeLaunchConfigurationsRequest request ->
      assert request.launchConfigurationNames == ['foo']
      def mockLaunch = Mock(LaunchConfiguration)
      mockLaunch.getLaunchConfigurationName() >> "foo"
      mockLaunch.getKeyName() >> "key-pair-name"
      mockLaunch.getBlockDeviceMappings() >> [new BlockDeviceMapping().withDeviceName('/dev/sdb').withEbs(new Ebs().withVolumeSize(125)), new BlockDeviceMapping().withDeviceName('/dev/sdc').withVirtualName('ephemeral1')]
      mockLaunch.getSpotPrice() >> ancestorSpotPrice
      new DescribeLaunchConfigurationsResult().withLaunchConfigurations([mockLaunch])
    }
    2 * mockAutoScaling.describeAutoScalingGroups(_) >> {
      def mockAsg = Mock(AutoScalingGroup)
      mockAsg.getAutoScalingGroupName() >> "asgard-stack-v000"
      mockAsg.getMinSize() >> 0
      mockAsg.getMaxSize() >> 2
      mockAsg.getDesiredCapacity() >> 4
      mockAsg.getLaunchConfigurationName() >> "foo"
      mockAsg.getTags() >> [new TagDescription().withKey('Name').withValue('name-tag')]
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups([mockAsg])
    }
    2 * serverGroupNameResolver.resolveLatestServerGroupName("asgard-stack") >> { "asgard-stack-v000" }
    0 * serverGroupNameResolver._
    1 * deployHandler.handle(expectedDescription(expectedSpotPrice, 'us-east-1'), _) >>
      new DeploymentResult(serverGroupNames: ['asgard-stack-v001'], serverGroupNameByRegion: ['us-east-1': 'asgard-stack-v001'])
    1 * deployHandler.handle(expectedDescription(expectedSpotPrice, 'us-west-1'), _) >>
      new DeploymentResult(serverGroupNames: ['asgard-stack-v001'], serverGroupNameByRegion: ['us-west-1': 'asgard-stack-v001'])

    where:
    requestSpotPrice | ancestorSpotPrice || expectedSpotPrice
    0.25             | null              || 0.25
    0.25             | 0.5               || 0.25
    null             | 0.25              || 0.25
    ""               | 0.25              || null
    null             | null              || null
  }

  @Unroll
  void "operation builds new description with correct cpu credits based on ancestor asg and request"() {
    given:
    def launchTemplateVersion = new LaunchTemplateVersion(
      launchTemplateName: "foo",
      launchTemplateId: "foo",
      versionNumber: 0,
      launchTemplateData: new ResponseLaunchTemplateData(
        keyName: "key-pair-name"
      )
    )
    if (ancestorUnlimitedCpuCredits != null) {
      launchTemplateVersion.launchTemplateData.creditSpecification = new CreditSpecification(
        cpuCredits: ancestorUnlimitedCpuCredits
      )
    }

    def launchTemplateSpec = new LaunchTemplateSpecification(
      launchTemplateName: launchTemplateVersion.launchTemplateName,
      launchTemplateId: launchTemplateVersion.launchTemplateId,
      version: launchTemplateVersion.versionNumber.toString(),
    )

    and:
    def requestDescription = new BasicAmazonDeployDescription(
      application: "asgard",
      stack: "stack",
      availabilityZones: [
        'us-east-1': []
      ],
      credentials: TestCredential.named('baz'),
      capacity: new BasicAmazonDeployDescription.Capacity(min: 1, max: 3, desired: 5),
      securityGroups: ["someGroupName", "sg-12345a"],
      setLaunchTemplate: true,
      unlimitedCpuCredits: unlimitedCpuCreditsInReq
    )

    and:
    def deployHandler = Mock(BasicAmazonDeployHandler)
    def mockAutoScaling = Mock(AmazonAutoScaling)
    def ec2 = Mock(AmazonEC2)
    def mockProvider = Mock(AmazonClientProvider)
    mockProvider.getAmazonEC2(_, _, true) >> ec2
    mockProvider.getAutoScaling(_, _, true) >> mockAutoScaling

    def op = new CopyLastAsgAtomicOperation(requestDescription)
    op.amazonClientProvider = mockProvider
    op.basicAmazonDeployHandler = deployHandler

    def mockAsgReferenceCopier = Mock(AsgReferenceCopier)
    def asgService = new AsgService(mockAutoScaling)
    def serverGroupNameResolver = Mock(AWSServerGroupNameResolver)

    op.regionScopedProviderFactory = Stub(RegionScopedProviderFactory) {
      forRegion(_, _) >> Stub(RegionScopedProviderFactory.RegionScopedProvider) {
        getAsgReferenceCopier(_, _) >> mockAsgReferenceCopier
        getAsgService() >> asgService
        getAWSServerGroupNameResolver() >> serverGroupNameResolver
        getLaunchTemplateService() >> Mock(LaunchTemplateService) {
          getLaunchTemplateVersion(launchTemplateSpec) >> Optional.of(launchTemplateVersion)
        }
      }
    }

    op.basicAmazonDeployDescriptionValidator = Stub(BasicAmazonDeployDescriptionValidator)

    and:
    def mockAncestorAsg = Mock(AutoScalingGroup)
    mockAncestorAsg.getAutoScalingGroupName() >> "asgard-stack-v000"
    mockAncestorAsg.getMinSize() >> 0
    mockAncestorAsg.getMaxSize() >> 2
    mockAncestorAsg.getDesiredCapacity() >> 4
    mockAncestorAsg.getLaunchTemplate() >> launchTemplateSpec
    mockAncestorAsg.getTags() >> [new TagDescription().withKey('Name').withValue('name-tag')]
    mockAutoScaling.describeAutoScalingGroups(_) >> {
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups([mockAncestorAsg])
    }

    serverGroupNameResolver.resolveLatestServerGroupName("asgard-stack") >> { "asgard-stack-v000" }

    when:
    op.operate([])

    then:
    1 * deployHandler.handle(expectedDescription(null, expectedUnlimitedCpuCredits, 'us-east-1'), _) >>
      new DeploymentResult(serverGroupNames: ['asgard-stack-v001'], serverGroupNameByRegion: ['us-east-1': 'asgard-stack-v001'])

    where:
    ancestorUnlimitedCpuCredits   ||  unlimitedCpuCreditsInReq  || expectedUnlimitedCpuCredits
    "standard"                    ||    true                    || true
    "standard"                    ||    false                   || false
    "standard"                    ||    null                    || null
    "unlimited"                   ||    true                    || true
    "unlimited"                   ||    false                   || false
    "unlimited"                   ||    null                    || null
  }

  private static BasicAmazonDeployDescription expectedDescription(
          Double expectedSpotPrice,
          Boolean unlimitedCpuCredits = null,
          String region) {
    return new BasicAmazonDeployDescription(
      application: 'asgard',
      stack: 'stack',
      keyPair: 'key-pair-name',
      securityGroups: ['someGroupName', 'sg-12345a'],
      availabilityZones: [(region): null],
      capacity: new BasicAmazonDeployDescription.Capacity(min: 1, max: 3, desired: 5),
      tags: [Name: 'name-tag'],
      spotPrice: expectedSpotPrice,
      source: new BasicAmazonDeployDescription.Source(
        asgName: "asgard-stack-v000",
        account: 'baz',
        region: null
      ),
      unlimitedCpuCredits: unlimitedCpuCredits
    )
  }
}
