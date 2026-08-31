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
import software.amazon.awssdk.services.autoscaling.AutoScalingClient
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup
import software.amazon.awssdk.services.autoscaling.model.BlockDeviceMapping
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsResponse
import software.amazon.awssdk.services.autoscaling.model.DescribeLaunchConfigurationsRequest
import software.amazon.awssdk.services.autoscaling.model.DescribeLaunchConfigurationsResponse
import software.amazon.awssdk.services.autoscaling.model.DescribeLifecycleHooksRequest
import software.amazon.awssdk.services.autoscaling.model.DescribeLifecycleHooksResponse
import software.amazon.awssdk.services.autoscaling.model.Ebs
import software.amazon.awssdk.services.autoscaling.model.InstancesDistribution
import software.amazon.awssdk.services.autoscaling.model.LaunchConfiguration
import software.amazon.awssdk.services.autoscaling.model.LaunchTemplate
import software.amazon.awssdk.services.autoscaling.model.LaunchTemplateOverrides
import software.amazon.awssdk.services.autoscaling.model.LaunchTemplateSpecification
import software.amazon.awssdk.services.autoscaling.model.LifecycleHook
import software.amazon.awssdk.services.autoscaling.model.MixedInstancesPolicy
import software.amazon.awssdk.services.autoscaling.model.TagDescription
import com.amazonaws.services.ec2.AmazonEC2
import software.amazon.awssdk.services.ec2.model.CreditSpecification
import software.amazon.awssdk.services.ec2.model.LaunchTemplateBlockDeviceMapping
import software.amazon.awssdk.services.ec2.model.LaunchTemplateEbsBlockDevice
import software.amazon.awssdk.services.ec2.model.LaunchTemplateInstanceMarketOptions
import software.amazon.awssdk.services.ec2.model.LaunchTemplateSpotMarketOptions
import software.amazon.awssdk.services.ec2.model.LaunchTemplateVersion
import software.amazon.awssdk.services.ec2.model.ResponseLaunchTemplateData
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
  def mockAutoScaling = Mock(AutoScalingClient)
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
    mockProvider.getAutoScalingV2(_, _) >> mockAutoScaling

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
    def launchTemplateVersion = LaunchTemplateVersion.builder()
      .launchTemplateName("foo")
      .launchTemplateId("foo")
      .versionNumber(0L)
      .launchTemplateData(ResponseLaunchTemplateData.builder()
        .keyName("key-pair-name")
        .instanceMarketOptions(LaunchTemplateInstanceMarketOptions.builder()
          .spotOptions(LaunchTemplateSpotMarketOptions.builder().maxPrice(ancestorSpotPrice).build())
          .build())
        .blockDeviceMappings(blockDevicesFromSrcAsg?.collect {
          LaunchTemplateBlockDeviceMapping.builder().virtualName(it.virtualName).deviceName(it.deviceName).build()
        } ?: [])
        .build())
      .build()

    def launchTemplateSpec = LaunchTemplateSpecification.builder()
      .launchTemplateName(launchTemplateVersion.launchTemplateName)
      .launchTemplateId(launchTemplateVersion.launchTemplateId)
      .version(launchTemplateVersion.versionNumber.toString())
      .build()

    and:
    regionScopedProviderStub.getLaunchTemplateService() >> Mock(LaunchTemplateService) {
      getLaunchTemplateVersion(launchTemplateSpec) >> Optional.of(launchTemplateVersion)
    }
    def mockAncestorAsg = AutoScalingGroup.builder()
      .autoScalingGroupName("asgard-stack-v000")
      .minSize(0)
      .maxSize(2)
      .desiredCapacity(4)
      .launchTemplate(launchTemplateSpec)
      .tags([TagDescription.builder().key('Name').value('name-tag').build()])
      .build()
    deployHandler.buildBlockDeviceMappingsFromSourceAsg(regionScopedProviderStub, mockAncestorAsg, description) >> blockDevicesFromSrcAsg

    when:
    def result = op.operate([])

    then:
    result.serverGroupNameByRegion['us-east-1'] == 'asgard-stack-v001'
    result.serverGroupNames == ['asgard-stack-v001']

    1 * mockAutoScaling.describeAutoScalingGroups(_) >> {
      DescribeAutoScalingGroupsResponse.builder().autoScalingGroups([mockAncestorAsg]).build()
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
      def mockLaunch = LaunchConfiguration.builder()
        .launchConfigurationName("foo")
        .keyName("key-pair-name")
        .blockDeviceMappings([
          BlockDeviceMapping.builder().deviceName('/dev/sdb').ebs(Ebs.builder().volumeSize(125).build()).build(),
          BlockDeviceMapping.builder().deviceName('/dev/sdc').virtualName('ephemeral1').build()
        ])
        .spotPrice(ancestorSpotPrice)
        .build()
      DescribeLaunchConfigurationsResponse.builder().launchConfigurations([mockLaunch]).build()
    }
    2 * mockAutoScaling.describeAutoScalingGroups(_) >> {
      def mockAsg = AutoScalingGroup.builder()
        .autoScalingGroupName("asgard-stack-v000")
        .minSize(0)
        .maxSize(2)
        .desiredCapacity(4)
        .launchConfigurationName("foo")
        .tags([TagDescription.builder().key('Name').value('name-tag').build()])
        .build()
      DescribeAutoScalingGroupsResponse.builder().autoScalingGroups([mockAsg]).build()
    }

    2 * serverGroupNameResolver.resolveLatestServerGroupName("asgard-stack") >> { "asgard-stack-v000" }
    0 * serverGroupNameResolver._
    1 * deployHandler.handle(expectedDescription(expectedSpotPrice, 'us-east-1', null, null, null, null, null, []), _) >>
      new DeploymentResult(serverGroupNames: ['asgard-stack-v001'], serverGroupNameByRegion: ['us-east-1': 'asgard-stack-v001'])
    1 * deployHandler.handle(expectedDescription(expectedSpotPrice, 'us-west-1', null, null, null, null, null, []), _) >>
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
    def rltdBuilder3 = ResponseLaunchTemplateData.builder().keyName("key-pair-name")
    if (ancestorUnlimitedCpuCredits != null) {
      rltdBuilder3.creditSpecification(CreditSpecification.builder().cpuCredits(ancestorUnlimitedCpuCredits).build())
    }
    def launchTemplateVersion = LaunchTemplateVersion.builder()
      .launchTemplateName("foo")
      .launchTemplateId("foo")
      .versionNumber(0L)
      .launchTemplateData(rltdBuilder3.build())
      .build()
    def launchTemplateSpec = LaunchTemplateSpecification.builder()
      .launchTemplateName(launchTemplateVersion.launchTemplateName)
      .launchTemplateId(launchTemplateVersion.launchTemplateId)
      .version(launchTemplateVersion.versionNumber.toString())
      .build()

    and:
    regionScopedProviderStub.getLaunchTemplateService() >> Mock(LaunchTemplateService) {
      getLaunchTemplateVersion(launchTemplateSpec) >> Optional.of(launchTemplateVersion)
    }

    and:
    def mockAncestorAsg = AutoScalingGroup.builder()
      .autoScalingGroupName("asgard-stack-v000")
      .minSize(0)
      .maxSize(2)
      .desiredCapacity(4)
      .launchTemplate(launchTemplateSpec)
      .tags([TagDescription.builder().key('Name').value('name-tag').build()])
      .build()
    mockAutoScaling.describeAutoScalingGroups(_) >> {
      DescribeAutoScalingGroupsResponse.builder().autoScalingGroups([mockAncestorAsg]).build()
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
    def launchTemplateVersion = LaunchTemplateVersion.builder()
      .launchTemplateName("foo")
      .launchTemplateId("foo")
      .versionNumber(0L)
      .launchTemplateData(ResponseLaunchTemplateData.builder()
        .keyName("key-pair-name")
        .blockDeviceMappings([
          LaunchTemplateBlockDeviceMapping.builder().deviceName("/dev/sdb").ebs(LaunchTemplateEbsBlockDevice.builder().volumeSize(125).build()).build(),
          LaunchTemplateBlockDeviceMapping.builder().deviceName("/dev/sdc").virtualName("ephemeral1").build()
        ])
        .build())
      .build()
    def launchTemplateSpec = LaunchTemplateSpecification.builder()
      .launchTemplateName(launchTemplateVersion.launchTemplateName)
      .launchTemplateId(launchTemplateVersion.launchTemplateId)
      .version(launchTemplateVersion.versionNumber.toString())
      .build()
    def ancestorMixedInstancesPolicy = MixedInstancesPolicy.builder()
      .launchTemplate(LaunchTemplate.builder()
        .launchTemplateSpecification(launchTemplateSpec)
        .overrides(ancestorOverrides ?: [])
        .build())
      .instancesDistribution(InstancesDistribution.builder()
        .onDemandAllocationStrategy("prioritized")
        .onDemandBaseCapacity(2)
        .onDemandPercentageAboveBaseCapacity(50)
        .spotAllocationStrategy(ancestorSpotAllocStrategy) // AWS default is lowest-price
        .spotInstancePools(ancestorSpotAllocStrategy == "lowest-price" ? 2 : null) // AWS default is 2
        .spotMaxPrice(ancestorSpotPrice)
        .build())
      .build()

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
      def mockAsg = AutoScalingGroup.builder()
        .autoScalingGroupName("asgard-stack-v000")
        .minSize(0)
        .maxSize(2)
        .desiredCapacity(4)
        .mixedInstancesPolicy(ancestorMixedInstancesPolicy)
        .tags([TagDescription.builder().key('Name').value('name-tag').build()])
        .build()
      DescribeAutoScalingGroupsResponse.builder().autoScalingGroups([mockAsg]).build()
    }

    and:
    1 * serverGroupNameResolver.resolveLatestServerGroupName("asgard-stack") >> { "asgard-stack-v000" }
    0 * serverGroupNameResolver._
    1 * deployHandler.handle(_ as BasicAmazonDeployDescription, _) >> { arguments ->
      def expectedMip = ancestorMixedInstancesPolicy.toBuilder()
        .instancesDistribution(ancestorMixedInstancesPolicy.instancesDistribution().toBuilder()
          .spotAllocationStrategy(expectedSpotAllocStrategy)
          .spotMaxPrice(expectedSpotPrice)
          .spotInstancePools(expectedSpotAllocStrategy == "lowest-price" ? 2 : null)
          .build())
        .build()
      def expectedDesc = expectedDescription(null, "us-east-1", null, null, expectedMip, expectedOverrides)
      def actualDesc = arguments[0]

      assert actualDesc == expectedDesc; new DeploymentResult(serverGroupNames: ['asgard-stack-v001'], serverGroupNameByRegion: ['us-east-1': 'asgard-stack-v001'])
    }

    where:
    requestSpotPrice | ancestorSpotPrice || expectedSpotPrice | requestSpotAllocStrategy | ancestorSpotAllocStrategy || expectedSpotAllocStrategy |                                   requestOverrides                        |                           ancestorOverrides                 ||        expectedOverrides
    "0.25"           | null              || "0.25"            |           null           |     "lowest-price"        ||   "lowest-price"          |                                      null                                 |                             null                            ||              null
    "0.25"           | "0.5"             || "0.25"            |   "capacity-optimized"   |     "lowest-price"        ||   "capacity-optimized"    |[new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(
                                                                                                                                                        instanceType: "c5.large", priority: 1),
                                                                                                                                                    new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(
                                                                                                                                                        instanceType: "c4.large", priority: 2)]                               |[LaunchTemplateOverrides.builder().instanceType("m5.large").weightedCapacity("1").build()]                             ||[new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(
                                                                                                                                                                                                                                                                                                   instanceType: "c5.large", priority: 1),
                                                                                                                                                                                                                                                                                               new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(
                                                                                                                                                                                                                                                                                                   instanceType: "c4.large", priority: 2)]
    null             | "0.25"            || "0.25"            |       null               |     "lowest-price"        ||     "lowest-price"        |                                      []                                   |                             null                            ||              null
    ""               | "0.25"            || null              |   "capacity-optimized"   |     "lowest-price"        ||    "capacity-optimized"   |                                      null                                 |[LaunchTemplateOverrides.builder().instanceType("m5.large").weightedCapacity("1").build(), LaunchTemplateOverrides.builder().instanceType("m5.xlarge").weightedCapacity("2").build()]                               ||[new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(
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

    def launchTemplateVersion = LaunchTemplateVersion.builder()
      .launchTemplateName("foo")
      .launchTemplateId("foo")
      .versionNumber(0L)
      .launchTemplateData(ResponseLaunchTemplateData.builder().keyName("key-pair-name").build())
      .build()

    def launchTemplateSpec = LaunchTemplateSpecification.builder()
      .launchTemplateName(launchTemplateVersion.launchTemplateName)
      .launchTemplateId(launchTemplateVersion.launchTemplateId)
      .version(launchTemplateVersion.versionNumber.toString())
      .build()

    and:
    regionScopedProviderStub.getLaunchTemplateService() >> Mock(LaunchTemplateService) {
      getLaunchTemplateVersion(launchTemplateSpec) >> Optional.of(launchTemplateVersion)
    }

    when:
    op.operate([])

    then:
    1 * mockAutoScaling.describeAutoScalingGroups(_) >> {
      def mockAsg = AutoScalingGroup.builder()
        .autoScalingGroupName("asgard-stack-v000")
        .minSize(0)
        .maxSize(2)
        .desiredCapacity(4)
        .launchTemplate(launchTemplateSpec)
        .capacityRebalance(ancestorCapRebalance)
        .build()
      DescribeAutoScalingGroupsResponse.builder().autoScalingGroups([mockAsg]).build()
    }
    (requestLifecycleHooks ? 0 : 1) * mockAutoScaling.describeLifecycleHooks(_ as DescribeLifecycleHooksRequest) >> { arguments ->
      DescribeLifecycleHooksRequest req = arguments[0]
      assert req.autoScalingGroupName() == "asgard-stack-v000"; DescribeLifecycleHooksResponse.builder().lifecycleHooks(ancestorLifecycleHooks ?: []).build()
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
          null          |       true           ||       true           | null                                                                              | [LifecycleHook.builder()
                                                                                                                                                              .lifecycleTransition('autoscaling:EC2_INSTANCE_TERMINATING')
                                                                                                                                                              .heartbeatTimeout(1800)
                                                                                                                                                              .defaultResult('CONTINUE').build()]                                  || [new AmazonAsgLifecycleHook(
                                                                                                                                                                                                                                lifecycleTransition: AmazonAsgLifecycleHook.Transition.EC2InstanceTerminating,
                                                                                                                                                                                                                                heartbeatTimeout: 1800,
                                                                                                                                                                                                                                defaultResult: AmazonAsgLifecycleHook.DefaultResult.CONTINUE)]
          false         |       false          ||       false          |[]                                                                                | [LifecycleHook.builder()
                                                                                                                                                              .lifecycleTransition('autoscaling:EC2_INSTANCE_TERMINATING')
                                                                                                                                                              .heartbeatTimeout(1800)
                                                                                                                                                              .defaultResult('CONTINUE').build()]                                  || [new AmazonAsgLifecycleHook(
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
          List<AmazonBlockDevice> blockDevices = null,
          List<String> classicLinkVpcSecurityGroups = null
  ) {
    def desc = new BasicAmazonDeployDescription(
      application: 'asgard',
      stack: 'stack',
      credentials: TestCredential.named('baz'),
      keyPair: 'key-pair-name',
      securityGroups: ['someGroupName', 'sg-12345a'],
      availabilityZones: [(region): []],
      enabledMetrics: [],
      loadBalancers: [],
      terminationPolicies: [],
      classicLinkVpcSecurityGroups: classicLinkVpcSecurityGroups,
      capacity: new BasicAmazonDeployDescription.Capacity(min: 1, max: 3, desired: 5),
      tags: [Name: 'name-tag'],
      lifecycleHooks: [],
      spotPrice: mip ? mip.instancesDistribution().spotMaxPrice() : expectedSpotPrice,
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

