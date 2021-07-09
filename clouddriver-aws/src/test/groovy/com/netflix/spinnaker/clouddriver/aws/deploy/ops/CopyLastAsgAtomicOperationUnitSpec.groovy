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
import com.amazonaws.services.autoscaling.model.DescribeLifecycleHooksRequest
import com.amazonaws.services.autoscaling.model.DescribeLifecycleHooksResult
import com.amazonaws.services.autoscaling.model.Ebs
import com.amazonaws.services.autoscaling.model.InstancesDistribution
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.amazonaws.services.autoscaling.model.LaunchTemplate
import com.amazonaws.services.autoscaling.model.LaunchTemplateOverrides
import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification
import com.amazonaws.services.autoscaling.model.LifecycleHook
import com.amazonaws.services.autoscaling.model.MixedInstancesPolicy
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
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType
import com.netflix.spinnaker.clouddriver.aws.deploy.validators.BasicAmazonDeployDescriptionValidator
import com.netflix.spinnaker.clouddriver.aws.model.AmazonAsgLifecycleHook
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice
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
import spock.lang.Subject
import spock.lang.Unroll

class CopyLastAsgAtomicOperationUnitSpec extends Specification {

  def deployHandler = Mock(BasicAmazonDeployHandler)
  def mockAutoScaling = Mock(AmazonAutoScaling)
  def ec2 = Mock(AmazonEC2)
  def mockProvider = Mock(AmazonClientProvider)
  def mockAsgReferenceCopier = Mock(AsgReferenceCopier)
  def asgService = new AsgService(mockAutoScaling)
  def serverGroupNameResolver = Mock(AWSServerGroupNameResolver)
  def regionScopedProviderStub = Stub(RegionScopedProviderFactory.RegionScopedProvider)

  def description = new BasicAmazonDeployDescription(
    application: "asgard",
    stack: "stack",
    availabilityZones: [
      'us-east-1': [],
      'us-west-1': []
    ],
    credentials: TestCredential.named('baz'),
    securityGroups: ["someGroupName", "sg-12345a"],
    capacity: new BasicAmazonDeployDescription.Capacity(min: 1, max: 3, desired: 5))

  @Subject def op = new CopyLastAsgAtomicOperation(description)

  def setup() {
    TaskRepository.threadLocalTask.set(Mock(Task))

    mockProvider.getAmazonEC2(_, _, true) >> ec2
    mockProvider.getAutoScaling(_, _, true) >> mockAutoScaling

    regionScopedProviderStub.getAsgReferenceCopier(_, _) >> mockAsgReferenceCopier
    regionScopedProviderStub.getAsgService() >> asgService
    regionScopedProviderStub.getAWSServerGroupNameResolver() >> serverGroupNameResolver

    op.amazonClientProvider = mockProvider
    op.basicAmazonDeployHandler = deployHandler
    op.regionScopedProviderFactory = Stub(RegionScopedProviderFactory) {
      forRegion(_, _) >> regionScopedProviderStub
    }
    op.basicAmazonDeployDescriptionValidator = Stub(BasicAmazonDeployDescriptionValidator)
  }

  @Unroll
  void "operation builds description based on ancestor asg backed by a launch template"() {
    given:
    description.availabilityZones = ['us-east-1': []]
    description.spotPrice = reqSpotPrice
    description.blockDevices = reqBlockDevices

    and:
    def blockDevicesFromSrcAsg = [new AmazonBlockDevice(deviceName: "/dev/src")]
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
        blockDeviceMappings: blockDevicesFromSrcAsg?.collect {
          new LaunchTemplateBlockDeviceMapping().withVirtualName(it.virtualName).withDeviceName(it.deviceName)
        }
      )
    )

    def launchTemplateSpec = new LaunchTemplateSpecification(
      launchTemplateName: launchTemplateVersion.launchTemplateName,
      launchTemplateId: launchTemplateVersion.launchTemplateId,
      version: launchTemplateVersion.versionNumber.toString(),
    )

    and:
    regionScopedProviderStub.getLaunchTemplateService() >> Mock(LaunchTemplateService) {
      getLaunchTemplateVersion(launchTemplateSpec) >> Optional.of(launchTemplateVersion)
    }
    def mockAncestorAsg = Mock(AutoScalingGroup)
    mockAncestorAsg.getAutoScalingGroupName() >> "asgard-stack-v000"
    mockAncestorAsg.getMinSize() >> 0
    mockAncestorAsg.getMaxSize() >> 2
    mockAncestorAsg.getDesiredCapacity() >> 4
    mockAncestorAsg.getLaunchTemplate() >> launchTemplateSpec
    mockAncestorAsg.getTags() >> [new TagDescription().withKey('Name').withValue('name-tag')]
    deployHandler.buildBlockDeviceMappingsFromSourceAsg(regionScopedProviderStub, mockAncestorAsg, description) >> blockDevicesFromSrcAsg

    when:
    def result = op.operate([])

    then:
    result.serverGroupNameByRegion['us-east-1'] == 'asgard-stack-v001'
    result.serverGroupNames == ['asgard-stack-v001']

    1 * mockAutoScaling.describeAutoScalingGroups(_) >> {
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups([mockAncestorAsg])
    }

    1 * serverGroupNameResolver.resolveLatestServerGroupName("asgard-stack") >> { "asgard-stack-v000" }
    0 * serverGroupNameResolver._
    1 * deployHandler.handle(_ as BasicAmazonDeployDescription, _) >> { arguments ->
      BasicAmazonDeployDescription actualDesc = arguments[0]
      def expectedDesc = expectedDescription(expectedSpotPrice, "us-east-1", null,null,null,null, expectedBlockDevices)

      assert actualDesc.blockDevices == expectedDesc.blockDevices
      assert actualDesc == expectedDesc; new DeploymentResult(serverGroupNames: ['asgard-stack-v001'], serverGroupNameByRegion: ['us-east-1': 'asgard-stack-v001'])
    }

    where:
    reqSpotPrice | ancestorSpotPrice || expectedSpotPrice ||              reqBlockDevices                    || expectedBlockDevices
        "0.25"   | null              || "0.25"            ||                    null                         || [new AmazonBlockDevice(deviceName: "/dev/src")]
        "0.25"   | "0.5"             || "0.25"            ||                     []                          || []
        null     | "0.25"            || "0.25"            || [new AmazonBlockDevice(deviceName: "/dev/req")] || [new AmazonBlockDevice(deviceName: "/dev/req")]
         ""      | "0.25"            || null              || [new AmazonBlockDevice(deviceName: "/dev/req")] || [new AmazonBlockDevice(deviceName: "/dev/req")]
        null     | null              || null              || [new AmazonBlockDevice(deviceName: "/dev/req")] || [new AmazonBlockDevice(deviceName: "/dev/req")]
  }

  @Unroll
  void "operation builds description based on ancestor asg backed by launch configuration"() {
    setup:
    description.spotPrice = requestSpotPrice

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
    "0.25"           | null              || "0.25"
    "0.25"           | "0.5"             || "0.25"
    null             | "0.25"            || "0.25"
    ""               | "0.25"            || null
    null             | null              || null
  }

  @Unroll
  void "operation builds new description with correct cpu credits based on ancestor asg and request"() {
    given:
    description.availabilityZones = ['us-east-1': []]
    description.setLaunchTemplate = true
    description.unlimitedCpuCredits = unlimitedCpuCreditsInReq
    description.instanceType = instanceTypeInReq

    def overrides = null
    if (instanceTypeOverride2InReq) {
      overrides = [ new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "t3.large", weightedCapacity: "2"),
                    new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: instanceTypeOverride2InReq, weightedCapacity: "4")]
      description.launchTemplateOverridesForInstanceType = overrides
    }

    and:
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
    regionScopedProviderStub.getLaunchTemplateService() >> Mock(LaunchTemplateService) {
      getLaunchTemplateVersion(launchTemplateSpec) >> Optional.of(launchTemplateVersion)
    }

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
    1 * deployHandler.handle(expectedDescription(null, 'us-east-1', instanceTypeInReq, expectedUnlimitedCpuCredits, null, overrides), _) >>
      new DeploymentResult(serverGroupNames: ['asgard-stack-v001'], serverGroupNameByRegion: ['us-east-1': 'asgard-stack-v001'])

    where:
    ancestorUnlimitedCpuCredits   ||  unlimitedCpuCreditsInReq || instanceTypeInReq   || instanceTypeOverride2InReq  || expectedUnlimitedCpuCredits
    "standard"                    ||    true                   ||     't2.large'      ||            null             || true
    "standard"                    ||    false                  ||     't2.large'      ||            null             || false
    "unlimited"                   ||    true                   ||     't2.large'      ||            null             || true
    "unlimited"                   ||    false                  ||     't2.large'      ||            null             || false
    "standard"                    ||    null                   ||     'c3.large'      ||            null             || null  // unsupported type, do NOT copy from ancestor
    "standard"                    ||    null                   ||     't3.large'      ||            null             || false // supported type, copy from ancestor
    "unlimited"                   ||    null                   ||     'c3.large'      ||            null             || null  // unsupported type, do NOT copy from ancestor
    "unlimited"                   ||    null                   ||     't3.large'      ||            null             || true  // supported type, copy from ancestor
    "standard"                    ||    null                   ||     't2.large'      ||       ['c4.large']          || null  // not all types supported, do NOT copy from ancestor
    "unlimited"                   ||    null                   ||     't2.large'      ||       ['c4.large']          || null  // not all types supported, do NOTcopy from ancestor
  }

  @Unroll
  void "operation builds description based on ancestor asg backed by mixed instances policy with launch template"() {
    given:
    description.availabilityZones = ['us-east-1': []]
    description.spotPrice = requestSpotPrice
    description.spotAllocationStrategy = requestSpotAllocStrategy
    description.launchTemplateOverridesForInstanceType = requestOverrides

    and:
    def launchTemplateVersion = new LaunchTemplateVersion(
      launchTemplateName: "foo",
      launchTemplateId: "foo",
      versionNumber: 0,
      launchTemplateData: new ResponseLaunchTemplateData(
        keyName: "key-pair-name",
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
    def ancestorMixedInstancesPolicy = new MixedInstancesPolicy(
      launchTemplate: new LaunchTemplate(
        launchTemplateSpecification: launchTemplateSpec,
        overrides: ancestorOverrides
      ),
      instancesDistribution: new InstancesDistribution(
        onDemandAllocationStrategy: "prioritized",
        onDemandBaseCapacity: 2,
        onDemandPercentageAboveBaseCapacity: 50,
        spotAllocationStrategy: ancestorSpotAllocStrategy, // AWS default is lowest-price
        spotInstancePools: ancestorSpotAllocStrategy == "lowest-price" ? 2 : null, // AWS default is 2
        spotMaxPrice: ancestorSpotPrice,
      )
    )

    and:
    regionScopedProviderStub.getLaunchTemplateService() >> Mock(LaunchTemplateService) {
      getLaunchTemplateVersion(launchTemplateSpec) >> Optional.of(launchTemplateVersion)
    }

    when:
    def result = op.operate([])

    then:
    result.serverGroupNameByRegion['us-east-1'] == 'asgard-stack-v001'
    result.serverGroupNames == ['asgard-stack-v001']

    1 * mockAutoScaling.describeAutoScalingGroups(_) >> {
      def mockAsg = Mock(AutoScalingGroup)
      mockAsg.getAutoScalingGroupName() >> "asgard-stack-v000"
      mockAsg.getMinSize() >> 0
      mockAsg.getMaxSize() >> 2
      mockAsg.getDesiredCapacity() >> 4
      mockAsg.getMixedInstancesPolicy() >> ancestorMixedInstancesPolicy
      mockAsg.getTags() >> [new TagDescription().withKey('Name').withValue('name-tag')]
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups([mockAsg])
    }

    and:
    1 * serverGroupNameResolver.resolveLatestServerGroupName("asgard-stack") >> { "asgard-stack-v000" }
    0 * serverGroupNameResolver._
    1 * deployHandler.handle(_ as BasicAmazonDeployDescription, _) >> { arguments ->
      def expectedMip = ancestorMixedInstancesPolicy
      expectedMip.setInstancesDistribution(ancestorMixedInstancesPolicy.getInstancesDistribution()
        .withSpotAllocationStrategy(expectedSpotAllocStrategy)
        .withSpotMaxPrice(expectedSpotPrice)
        .withSpotInstancePools(expectedSpotAllocStrategy == "lowest-price" ? 2 : null))
      expectedMip.setLaunchTemplate(ancestorMixedInstancesPolicy.getLaunchTemplate().withOverrides(expectedOverrides))
      def expectedDesc = expectedDescription(null, "us-east-1", null, null, expectedMip)
      def actualDesc = arguments[0]

      assert actualDesc == expectedDesc; new DeploymentResult(serverGroupNames: ['asgard-stack-v001'], serverGroupNameByRegion: ['us-east-1': 'asgard-stack-v001'])
    }

    where:
    requestSpotPrice | ancestorSpotPrice || expectedSpotPrice | requestSpotAllocStrategy | ancestorSpotAllocStrategy || expectedSpotAllocStrategy |                                   requestOverrides                        |                           ancestorOverrides                 ||        expectedOverrides
    "0.25"           | null              || "0.25"            |           null           |     "lowest-price"        ||   "lowest-price"          |                                      null                                 |                             null                            ||              null
    "0.25"           | "0.5"             || "0.25"            |   "capacity-optimized"   |     "lowest-price"        ||   "capacity-optimized"    |[new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(
                                                                                                                                                        instanceType: "c5.large", priority: 1),
                                                                                                                                                    new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(
                                                                                                                                                        instanceType: "c4.large", priority: 2)]                               |[new LaunchTemplateOverrides().withInstanceType("m5.large")
                                                                                                                                                                                                                                    .withWeightedCapacity("1")]                             ||[new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(
                                                                                                                                                                                                                                                                                                   instanceType: "c5.large", priority: 1),
                                                                                                                                                                                                                                                                                               new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(
                                                                                                                                                                                                                                                                                                   instanceType: "c4.large", priority: 2)]
    null             | "0.25"            || "0.25"            |       null               |     "lowest-price"        ||     "lowest-price"        |                                      []                                   |                             null                            ||              null
    ""               | "0.25"            || null              |   "capacity-optimized"   |     "lowest-price"        ||    "capacity-optimized"   |                                      null                                 |[new LaunchTemplateOverrides().withInstanceType("m5.large")
                                                                                                                                                                                                                                  .withWeightedCapacity("1"),
                                                                                                                                                                                                                                new LaunchTemplateOverrides().withInstanceType("m5.xlarge")
                                                                                                                                                                                                                                  .withWeightedCapacity("2")]                               ||[new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(
                                                                                                                                                                                                                                                                                                   instanceType: "m5.large", weightedCapacity: "1", priority: 1),
                                                                                                                                                                                                                                                                                               new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(
                                                                                                                                                                                                                                                                                                   instanceType: "m5.xlarge", weightedCapacity: "2", priority: 2)]
    null             | null              || null              |       null               |     "lowest-price"        ||     "lowest-price"         |                                      null                                 |                             null                             ||              null
  }

  @Unroll
  void "operation populates ASG lifecycle hooks and capacity rebalance in description as expected"() {
    given:
    description.availabilityZones = ['us-east-1': []]
    description.lifecycleHooks = requestLifecycleHooks
    description.capacityRebalance = requestCapRebalance

    def launchTemplateVersion = new LaunchTemplateVersion(
      launchTemplateName: "foo",
      launchTemplateId: "foo",
      versionNumber: 0,
      launchTemplateData: new ResponseLaunchTemplateData(
        keyName: "key-pair-name",
        )
      )

    def launchTemplateSpec = new LaunchTemplateSpecification(
      launchTemplateName: launchTemplateVersion.launchTemplateName,
      launchTemplateId: launchTemplateVersion.launchTemplateId,
      version: launchTemplateVersion.versionNumber.toString(),
    )

    and:
    regionScopedProviderStub.getLaunchTemplateService() >> Mock(LaunchTemplateService) {
      getLaunchTemplateVersion(launchTemplateSpec) >> Optional.of(launchTemplateVersion)
    }

    when:
    op.operate([])

    then:
    1 * mockAutoScaling.describeAutoScalingGroups(_) >> {
      def mockAsg = Mock(AutoScalingGroup)
      mockAsg.getAutoScalingGroupName() >> "asgard-stack-v000"
      mockAsg.getMinSize() >> 0
      mockAsg.getMaxSize() >> 2
      mockAsg.getDesiredCapacity() >> 4
      mockAsg.getLaunchTemplate() >> launchTemplateSpec
      mockAsg.getCapacityRebalance() >> ancestorCapRebalance
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups([mockAsg])
    }
    (requestLifecycleHooks ? 0 : 1) * mockAutoScaling.describeLifecycleHooks(_ as DescribeLifecycleHooksRequest) >> { arguments ->
      DescribeLifecycleHooksRequest req = arguments[0]
      assert req.getAutoScalingGroupName() == "asgard-stack-v000"; new DescribeLifecycleHooksResult().withLifecycleHooks(ancestorLifecycleHooks)
    }

    and:
    1 * serverGroupNameResolver.resolveLatestServerGroupName("asgard-stack") >> { "asgard-stack-v000" }
    0 * serverGroupNameResolver._
    1 * deployHandler.handle(_ as BasicAmazonDeployDescription, _) >> { arguments ->
      BasicAmazonDeployDescription actualDesc = arguments[0]

      assert actualDesc.capacityRebalance == expectedCapRebalance
      assert actualDesc.lifecycleHooks == expectedLifecycleHooks; new DeploymentResult(serverGroupNames: ['asgard-stack-v001'], serverGroupNameByRegion: ['us-east-1': 'asgard-stack-v001'])
    }

    where:
    requestCapRebalance | ancestorCapRebalance || expectedCapRebalance | requestLifecycleHooks                                                             | ancestorLifecycleHooks                                        || expectedLifecycleHooks
          null          |       false          ||       false          | null                                                                              |      null                                                     ||  []
          null          |       true           ||       true           | null                                                                              | [new LifecycleHook(
                                                                                                                                                              lifecycleTransition: 'autoscaling:EC2_INSTANCE_TERMINATING',
                                                                                                                                                              heartbeatTimeout: 1800,
                                                                                                                                                              defaultResult: 'CONTINUE')]                                  || [new AmazonAsgLifecycleHook(
                                                                                                                                                                                                                                lifecycleTransition: AmazonAsgLifecycleHook.Transition.EC2InstanceTerminating,
                                                                                                                                                                                                                                heartbeatTimeout: 1800,
                                                                                                                                                                                                                                defaultResult: AmazonAsgLifecycleHook.DefaultResult.CONTINUE)]
          false         |       false          ||       false          |[]                                                                                | [new LifecycleHook(
                                                                                                                                                              lifecycleTransition: 'autoscaling:EC2_INSTANCE_TERMINATING',
                                                                                                                                                              heartbeatTimeout: 1800,
                                                                                                                                                              defaultResult: 'CONTINUE')]                                  || [new AmazonAsgLifecycleHook(
                                                                                                                                                                                                                                lifecycleTransition: AmazonAsgLifecycleHook.Transition.EC2InstanceTerminating,
                                                                                                                                                                                                                                heartbeatTimeout: 1800,
                                                                                                                                                                                                                                defaultResult: AmazonAsgLifecycleHook.DefaultResult.CONTINUE)]
          true          |       false          ||       true           |[new AmazonAsgLifecycleHook(
                                                                          roleARN: 'role-arn',
                                                                          notificationTargetARN: 'target-arn',
                                                                          notificationMetadata: 'metadata',
                                                                          lifecycleTransition: AmazonAsgLifecycleHook.Transition.EC2InstanceTerminating,
                                                                          heartbeatTimeout: 3600,
                                                                          defaultResult: AmazonAsgLifecycleHook.DefaultResult.ABANDON
                                                                         )]                                                                                |                            null                                || [new AmazonAsgLifecycleHook(
                                                                                                                                                            roleARN: 'role-arn',
                                                                                                                                                            notificationTargetARN: 'target-arn',
                                                                                                                                                            notificationMetadata: 'metadata',
                                                                                                                                                            lifecycleTransition: AmazonAsgLifecycleHook.Transition.EC2InstanceTerminating,
                                                                                                                                                            heartbeatTimeout: 3600,
                                                                                                                                                            defaultResult: AmazonAsgLifecycleHook.DefaultResult.ABANDON)]
  }

  private static BasicAmazonDeployDescription expectedDescription(
          String expectedSpotPrice = null,
          String region,
          String instanceType = null,
          Boolean unlimitedCpuCredits = null,
          MixedInstancesPolicy mip = null,
          List<LaunchTemplateOverridesForInstanceType> overrides = null,
          List<AmazonBlockDevice> blockDevices = null
  ) {
    def desc = new BasicAmazonDeployDescription(
      application: 'asgard',
      stack: 'stack',
      credentials: TestCredential.named('baz'),
      keyPair: 'key-pair-name',
      securityGroups: ['someGroupName', 'sg-12345a'],
      availabilityZones: [(region): null],
      capacity: new BasicAmazonDeployDescription.Capacity(min: 1, max: 3, desired: 5),
      tags: [Name: 'name-tag'],
      lifecycleHooks: [],
      spotPrice: mip ? mip.getInstancesDistribution().getSpotMaxPrice() : expectedSpotPrice,
      source: new BasicAmazonDeployDescription.Source(
        asgName: "asgard-stack-v000",
        account: 'baz',
        region: null
      ),
      unlimitedCpuCredits: unlimitedCpuCredits,
      instanceType: instanceType,
      blockDevices: blockDevices
    )

    if (mip) {
      desc.onDemandAllocationStrategy = mip.instancesDistribution.onDemandAllocationStrategy
      desc.onDemandBaseCapacity = mip.instancesDistribution.onDemandBaseCapacity
      desc.onDemandPercentageAboveBaseCapacity = mip.instancesDistribution.onDemandPercentageAboveBaseCapacity
      desc.spotAllocationStrategy = mip.instancesDistribution.spotAllocationStrategy
      desc.spotInstancePools = mip.instancesDistribution.spotInstancePools
      int priority = 1
      desc.launchTemplateOverridesForInstanceType = mip.launchTemplate.overrides ? mip.launchTemplate.overrides.collect {
          new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: it.instanceType, weightedCapacity: it.weightedCapacity, priority: priority++)
        }.toList() : null
    }
    if (overrides) {
      desc.launchTemplateOverridesForInstanceType = overrides
    }

    return desc
  }
}

