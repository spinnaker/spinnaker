/*
 * Copyright 2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.asg.asgbuilders

import software.amazon.awssdk.services.autoscaling.AutoScalingClient
import software.amazon.awssdk.services.autoscaling.model.AlreadyExistsException
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsResponse
import software.amazon.awssdk.services.autoscaling.model.InstancesDistribution
import software.amazon.awssdk.services.autoscaling.model.LaunchTemplateOverrides
import software.amazon.awssdk.services.autoscaling.model.LaunchTemplateSpecification
import software.amazon.awssdk.services.autoscaling.model.MixedInstancesPolicy
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.DescribeSubnetsResponse
import software.amazon.awssdk.services.ec2.model.LaunchTemplate
import software.amazon.awssdk.services.ec2.model.Subnet
import software.amazon.awssdk.services.ec2.model.Tag
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AsgConfigHelper
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AsgLifecycleHookWorker
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AutoScalingWorker
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.clouddriver.aws.model.AmazonAsgLifecycleHook
import com.netflix.spinnaker.clouddriver.aws.services.LaunchTemplateService
import com.netflix.spinnaker.clouddriver.aws.services.SecurityGroupService
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataOverride
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.config.AwsConfiguration
import org.apache.commons.lang3.StringUtils
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant
import java.time.temporal.ChronoUnit

import static com.netflix.spinnaker.clouddriver.aws.model.AmazonAsgLifecycleHook.DefaultResult.CONTINUE
import static com.netflix.spinnaker.clouddriver.aws.model.AmazonAsgLifecycleHook.Transition.EC2InstanceLaunching

class AsgWithMixedInstancesPolicyBuilderSpec extends Specification {

  def ec2LtService = Mock(LaunchTemplateService)
  def securityGroupService = Mock(SecurityGroupService)
  def deployDefaults = Mock(AwsConfiguration.DeployDefaults)
  def autoScaling = Mock(AutoScalingClient)
  def amazonEC2 = Mock(Ec2Client)
  def asgLifecycleHookWorker = Mock(AsgLifecycleHookWorker)

  def credential = TestCredential.named('foo')
  def userDataOverride = Mock(UserDataOverride)
  def task = Mock(Task)
  def taskPhase = "AWS_DEPLOY_TEST"
  def asgConfig, asgName, asgConfigHelper
  def ec2Lt, asgLtSpec, asgLt, overrides, instancesDist, mip, override1, override2

  def setup() {
    asgConfigHelper = Spy(AsgConfigHelper)
    override1 = new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(
      instanceType: "some.type.large",
      weightedCapacity: "2")
    override2 = new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(
      instanceType: "some.type.xlarge",
      weightedCapacity: "4")
    asgConfig = AutoScalingWorker.AsgConfiguration.builder()
      .setLaunchTemplate(true)
      .credentials(credential)
      .legacyUdf(false)
      .application("myasg")
      .region("us-east-1")
      .minInstances(1)
      .maxInstances(3)
      .desiredInstances(2)
      .instanceType("some.type.medium")
      .securityGroups(["my-sg"])
      .spotMaxPrice("2")
      .onDemandBaseCapacity(1)
      .onDemandPercentageAboveBaseCapacity(50)
      .spotAllocationStrategy("capacity-optimized")
      .launchTemplateOverridesForInstanceType([override1, override2])
      .build()

    asgName = "myasg-v000"
    securityGroupService.resolveSecurityGroupIdsWithSubnetType(_,_) >> ["sg-1"]

    // general expected parameters in request
    ec2Lt = LaunchTemplate.builder().launchTemplateName("lt-1").launchTemplateId("lt-1").latestVersionNumber(1L).defaultVersionNumber(0L).build()
    asgLtSpec = LaunchTemplateSpecification.builder().launchTemplateId(ec2Lt.launchTemplateId()).version("\$Latest").build()
    overrides = [
      LaunchTemplateOverrides.builder().instanceType(override1.instanceType).weightedCapacity(override1.weightedCapacity).build(),
      LaunchTemplateOverrides.builder().instanceType(override2.instanceType).weightedCapacity(override2.weightedCapacity).build()
    ]
    asgLt = software.amazon.awssdk.services.autoscaling.model.LaunchTemplate.builder().launchTemplateSpecification(asgLtSpec).overrides(overrides).build()
    instancesDist = InstancesDistribution.builder()
      .onDemandBaseCapacity(1)
      .onDemandPercentageAboveBaseCapacity(50)
      .spotMaxPrice("2")
      .spotAllocationStrategy("capacity-optimized")
      .build()
    mip = MixedInstancesPolicy.builder().instancesDistribution(instancesDist).launchTemplate(asgLt).build()
  }

  private DescribeSubnetsResponse getDescribeSubnetsResult() {
    return DescribeSubnetsResponse.builder().subnets([
      Subnet.builder().subnetId('subnetId1').availabilityZone('us-east-1a').tags([Tag.builder().key('immutable_metadata').value('{"purpose": "internal", "target": "ec2" }').build()]).build(),
      Subnet.builder().subnetId('subnetId2').availabilityZone('us-west-2a').build(),
      Subnet.builder().subnetId('subnetId3').availabilityZone('us-west-2a').build(),
    ]).build()
  }

  void "should build ASG request with mixed instances policy correctly"() {
    given:
    def asgWithMipBuilder = new AsgWithMixedInstancesPolicyBuilder(ec2LtService, securityGroupService, deployDefaults,  autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.availabilityZones = ["us-east-1a"]
    asgConfig.subnetType = "internal"
    asgConfig.classicLoadBalancers = ["one", "two"]
    asgConfig.targetGroupArns = ["tg1", "tg2"]
    asgConfig.cooldown = 5
    asgConfig.healthCheckGracePeriod = 5
    asgConfig.healthCheckType = "ec2"
    asgConfig.terminationPolicies = ["Default", "OldestInstance"]
    asgConfig.userDataOverride = userDataOverride
    asgConfig.ebsOptimized = true

    when:
    def request = asgWithMipBuilder.buildRequest(task, taskPhase, asgName, asgConfig)

    then:
    1 * ec2LtService.createLaunchTemplate(asgConfig, asgName, _) >> ec2Lt

    1 * task.updateStatus(taskPhase, "Deploying ASG $asgName with mixed instances policy " + mip.toString())
    2 * amazonEC2.describeSubnets() >> getDescribeSubnetsResult()

    and:
    request.launchTemplate() == null
    request.mixedInstancesPolicy() == mip

    and:
    request.autoScalingGroupName() == asgName
    request.minSize() == 1
    request.maxSize() == 3
    request.desiredCapacity() == 2
    request.loadBalancerNames() == ["one", "two"]
    request.targetGroupARNs() == ["tg1", "tg2"]
    request.defaultCooldown() == 5
    request.healthCheckGracePeriod() == 5
    request.healthCheckType() == "ec2"
    request.terminationPolicies() == ["Default", "OldestInstance"]
  }

  void "should build ASG request with tags correctly"() {
    given:
    def asgWithMipBuilder = new AsgWithMixedInstancesPolicyBuilder(ec2LtService, securityGroupService, deployDefaults,  autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.tags = [foo: "bar"]

    when:
    def request = asgWithMipBuilder.buildRequest(task, taskPhase, asgName, asgConfig)

    then:
    1 * ec2LtService.createLaunchTemplate(asgConfig, asgName, _) >> ec2Lt
    request.mixedInstancesPolicy() == mip
    def tag = request.tags()[0]
    tag.key() == "foo"
    tag.value() == "bar"
    tag.propagateAtLaunch() == true
  }

  @Unroll
  void "should favor subnetIds over AZ while building ASG request"() {
    given:
    def asgWithMipBuilder = new AsgWithMixedInstancesPolicyBuilder(ec2LtService, securityGroupService, deployDefaults,  autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.availabilityZones = ["us-west-2a"]
    asgConfig.subnetIds = subnetIds
    asgConfig.subnetType = subnetType

    when:
    def request = asgWithMipBuilder.buildRequest(task, taskPhase, asgName, asgConfig)

    then:
    1 * ec2LtService.createLaunchTemplate(asgConfig, asgName, _) >> ec2Lt
    (StringUtils.isEmpty(subnetType) ? 0 : 2) * amazonEC2.describeSubnets() >> getDescribeSubnetsResult()

    1 * task.updateStatus(taskPhase, "Deploying ASG $asgName with mixed instances policy " + mip.toString())
    1 * task.updateStatus(taskPhase, deployMsg)

    and:
    request.mixedInstancesPolicy() == mip
    request.vpcZoneIdentifier() == subnetIdsForAsg
    subnetIdsForAsg == null
      ? request.availabilityZones() == ["us-west-2a"]
      : request.availabilityZones() == []

    where:
    subnetType |  subnetIds   | subnetIdsForAsg ||  deployMsg
    "internal" |["subnetId3"] | "subnetId3"     || " > Deploying to subnetIds: subnetId3"
    "internal" |["subnetId2"] | "subnetId2"     || " > Deploying to subnetIds: subnetId2"
    null       |    null      |       null      || "Deploying to availabilityZones: [us-west-2a]"
    null       |      []      |       null      || "Deploying to availabilityZones: [us-west-2a]"
  }

  @Unroll
  void "should filter and validate subnets by AZ while building ASG request"() {
    given:
    def asgWithMipBuilder = new AsgWithMixedInstancesPolicyBuilder(ec2LtService, securityGroupService, deployDefaults,  autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.availabilityZones =  ["us-east-1a"]
    asgConfig.subnetIds = subnetIds
    asgConfig.subnetType = "internal"

    when:
    def request = asgWithMipBuilder.buildRequest(task, taskPhase, asgName, asgConfig)

    then:
    1 * ec2LtService.createLaunchTemplate(asgConfig, asgName, _) >> ec2Lt
    2 * amazonEC2.describeSubnets() >> getDescribeSubnetsResult()
    1 * task.updateStatus(taskPhase, "Deploying ASG $asgName with mixed instances policy " + mip.toString())
    1 * task.updateStatus(taskPhase, " > Deploying to subnetIds: subnetId1")

    and:
    request.mixedInstancesPolicy() == mip
    request.vpcZoneIdentifier() == subnetIdsForAsg
    request.availabilityZones() == []

    where:
    subnetIds     |   subnetIdsForAsg
    []            |  "subnetId1"   // filter subnets by AZ
    ["subnetId1"] |  "subnetId1"   // validate subnets by AZ
  }

  @Unroll
  void "throws exception when invalid subnet IDs are specified while building ASG request"() {
    given:
    def asgWithMipBuilder = new AsgWithMixedInstancesPolicyBuilder(ec2LtService, securityGroupService, deployDefaults,  autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.availabilityZones = availabilityZones
    asgConfig.subnetIds = subnetIds
    asgConfig.subnetType = subnetType

    when:
    asgWithMipBuilder.buildRequest(task, taskPhase, asgName, asgConfig)

    then:
    1 * ec2LtService.createLaunchTemplate(asgConfig, asgName, _) >> ec2Lt
    (StringUtils.isEmpty(subnetType) ? 0 : 1) * amazonEC2.describeSubnets() >> getDescribeSubnetsResult()

    1 * task.updateStatus(taskPhase, "Deploying ASG $asgName with mixed instances policy " + mip.toString())

    and:
    def ex = thrown(IllegalStateException)
    ex.getMessage() == String.format(
      "One or more subnet ids are not valid (invalidSubnetIds: %s, availabilityZones: %s)",
      invalidSubnetIds, String.join(",", availabilityZones))

    where:
    subnetType  |            subnetIds                      |  availabilityZones  | invalidSubnetIds
    null        |          ["subnetId1"]                    |   ["us-west-2a"]    |   "subnetId1"
    ""          |          ["subnetId2"]                    |   ["us-east-1a"]    |   "subnetId2"
    "internal"  |          ["subnetId3"]                    |   ["us-east-1a"]    |   "subnetId3"
    "internal"  |          ["invalidSubnetId"]              |         []          |   "invalidSubnetId"
    "internal"  |         ["subnetId1", "subnetId2"]        |   ["us-east-1a"]    |   "subnetId2"
    "internal"  |  ["subnetId1", "subnetId2", "subnetId3"]  |   ["us-west-2a"]    |   "subnetId1"
  }

  @Unroll
  void "should filter subnets by subnet purpose conditionally while building ASG request"() {
    given:
    def asgWithMipBuilder = new AsgWithMixedInstancesPolicyBuilder(ec2LtService, securityGroupService, deployDefaults,  autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.availabilityZones = []
    asgConfig.subnetType = "internal"
    asgConfig.subnetIds = subnetIds

    when:
    def request = asgWithMipBuilder.buildRequest(task, taskPhase, asgName, asgConfig)

    then:
    1 * ec2LtService.createLaunchTemplate(asgConfig, asgName, _) >> ec2Lt
    2 * amazonEC2.describeSubnets() >> getDescribeSubnetsResult()

    1 * task.updateStatus(taskPhase, "Deploying ASG $asgName with mixed instances policy " + mip.toString())
    1 * task.updateStatus(taskPhase, deployMsg)

    and:
    request.mixedInstancesPolicy() == mip
    request.vpcZoneIdentifier() == subnetIdsForAsg
    request.availabilityZones() == []

    where:
    subnetIds         | subnetIdsForAsg       ||  deployMsg
    []              |  "subnetId1"          || " > Deploying to subnetIds: subnetId1"  // filtered by subnet purpose tags
    ["subnetId2"]       |   "subnetId2"         || " > Deploying to subnetIds: subnetId2"  // not filtered by subnet purpose tags
    ["subnetId1","subnetId2"] | "subnetId1,subnetId2" || " > Deploying to subnetIds: subnetId1,subnetId2" // not filtered by subnet purpose tags
  }

  @Unroll
  void "throws exception when subnetIds are not specified and no suitable subnet found for subnet purpose while building ASG request"() {
    given:
    def asgWithMipBuilder = new AsgWithMixedInstancesPolicyBuilder(ec2LtService, securityGroupService, deployDefaults,  autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.subnetIds = []
    asgConfig.availabilityZones = availabilityZones
    asgConfig.subnetType = subnetType

    when:
    asgWithMipBuilder.buildRequest(task, taskPhase, asgName, asgConfig)

    then:
    1 * ec2LtService.createLaunchTemplate(asgConfig, asgName, _) >> ec2Lt
    2 * amazonEC2.describeSubnets() >> getDescribeSubnetsResult()

    1 * task.updateStatus(taskPhase, "Deploying ASG $asgName with mixed instances policy " + mip.toString())

    and:
    def ex = thrown(RuntimeException)
    ex.getMessage() == "No suitable subnet was found for internal subnet purpose '$subnetType'!"

    where:
    subnetType   | availabilityZones
    "internal"   | ["eu-central-1a"]
    "internal"   | ["us-west-2a"]
    "unknown"    | ["us-west-1b"]
  }

  void "should build ASG with mixed instances policy correctly"() {
    given:
    def asgWithMipBuilder = new AsgWithMixedInstancesPolicyBuilder(ec2LtService, securityGroupService, deployDefaults,  autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.availabilityZones = ["us-east-1a"]
    asgConfig.subnetType = "internal"
    asgConfig.classicLoadBalancers = ["one", "two"]
    asgConfig.targetGroupArns = ["tg1", "tg2"]
    asgConfig.userDataOverride = userDataOverride
    asgConfig.securityGroups = ["mysg"]

    when:
    def asgNameRes = asgWithMipBuilder.build(task, taskPhase, asgName, asgConfig)

    then:
    asgNameRes == asgName
    1 * ec2LtService.createLaunchTemplate(asgConfig, asgName, _) >> ec2Lt
    2 * amazonEC2.describeSubnets() >> getDescribeSubnetsResult()
    1 * autoScaling.createAutoScalingGroup(_)
    1 * autoScaling.updateAutoScalingGroup(_)
    0 * autoScaling. _

    1 * task.updateStatus(taskPhase, "Deploying ASG $asgName with mixed instances policy " + mip.toString())
    1 * task.updateStatus(taskPhase, ' > Deploying to subnetIds: subnetId1')
    1 * task.updateStatus(taskPhase, 'Setting size of myasg-v000 in foo/us-east-1 to [min=1, max=3, desired=2]')
    1 * task.updateStatus(taskPhase, "Deployed EC2 server group named $asgName")
  }

  @Unroll
  void "does not enable metrics collection when enabledMetrics are absent or instanceMonitoring is falsy"() {
    setup:
    def asgWithMipBuilder = new AsgWithMixedInstancesPolicyBuilder(ec2LtService, securityGroupService, deployDefaults,  autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.enabledMetrics = enabledMetrics
    asgConfig.instanceMonitoring = instanceMonitoring

    when:
    asgWithMipBuilder.build(task, taskPhase, asgName, asgConfig)

    then:
    1 * ec2LtService.createLaunchTemplate(asgConfig, asgName, _) >> ec2Lt
    count * autoScaling.enableMetricsCollection({count == 1 ? it.metrics() == ['GroupMinSize', 'GroupMaxSize'] : _ })

    where:
    enabledMetrics                   | instanceMonitoring  | count
    null                             | null                |  0
    []                               | null                |  0
    []                               | false               |  0
    ['GroupMinSize', 'GroupMaxSize'] | null                |  0
    ['GroupMinSize', 'GroupMaxSize'] | []                  |  0
    ['GroupMinSize', 'GroupMaxSize'] | false               |  0
    ['GroupMinSize', 'GroupMaxSize'] | true                |  1
  }

  void "enables metrics collection for all metrics when enabledMetrics is an empty list and instanceMonitoring is true"() {
    setup:
    def asgWithMipBuilder = new AsgWithMixedInstancesPolicyBuilder(ec2LtService, securityGroupService, deployDefaults,  autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.enabledMetrics = []
    asgConfig.instanceMonitoring = true

    when:
    asgWithMipBuilder.build(task, taskPhase, asgName, asgConfig)

    then:
    1 * ec2LtService.createLaunchTemplate(asgConfig, asgName, _) >> ec2Lt
    // According to
    // https://docs.aws.amazon.com/autoscaling/ec2/APIReference/API_EnableMetricsCollection.html,
    // specifying granularity with no metrics means all metrics.
    1 * autoScaling.enableMetricsCollection({ (it.granularity() == '1Minute') && (it.metrics() == []) })
  }

  void "continues if serverGroup already exists, is reasonably the same and within safety window"() {
    given:
    def asgWithMipBuilder = new AsgWithMixedInstancesPolicyBuilder(ec2LtService, securityGroupService, deployDefaults,  autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.classicLoadBalancers = ["one", "two"]

    when:
    asgWithMipBuilder.build(task, taskPhase, asgName, asgConfig)

    then:
    noExceptionThrown()
    1 * ec2LtService.createLaunchTemplate(asgConfig, asgName, _) >> ec2Lt
    1 * autoScaling.createAutoScalingGroup(_) >> { throw AlreadyExistsException.builder().message("Already exists, man").build() }
    1 * autoScaling.describeAutoScalingGroups(_) >> {
      DescribeAutoScalingGroupsResponse.builder().autoScalingGroups([
        AutoScalingGroup.builder()
          .autoScalingGroupName("myasg-v000")
          .mixedInstancesPolicy(mip)
          .loadBalancerNames(["one", "two"])
          .createdTime(Instant.now())
          .build()
      ]).build()
    }
  }

  @Unroll
  void "continues if serverGroup already exists, and existing and desired autoscaling group have the same configuration"() {
    given:
    def asgWithMipBuilder = new AsgWithMixedInstancesPolicyBuilder(ec2LtService, securityGroupService, deployDefaults,  autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.subnetType = sbTypeReq
    asgConfig.subnetIds = sbReq
    asgConfig.availabilityZones = azReq
    asgConfig.classicLoadBalancers = ["two", "one"]
    asgConfig.targetGroupArns = ["tg2", "tg1"]
    asgConfig.cooldown = 5
    asgConfig.healthCheckGracePeriod = 5
    asgConfig.healthCheckType = "elb"
    asgConfig.terminationPolicies = ["tp2", "tp1"]

    when:
    asgWithMipBuilder.build(task, taskPhase, asgName, asgConfig)

    then:
    noExceptionThrown()
    1 * ec2LtService.createLaunchTemplate(asgConfig, asgName, _) >> ec2Lt
    if (sbTypeReq != null) {
      2 * amazonEC2.describeSubnets() >> DescribeSubnetsResponse.builder().subnets([Subnet.builder().subnetId('sb1').availabilityZone('us-east-1a').build(), Subnet.builder().subnetId('sb2').availabilityZone('us-east-1b').build(),]).build()
    }
    1 * autoScaling.createAutoScalingGroup(_) >> { throw AlreadyExistsException.builder().message("Already exists, man").build() }
    1 * autoScaling.describeAutoScalingGroups(_) >> {
      DescribeAutoScalingGroupsResponse.builder().autoScalingGroups([
        AutoScalingGroup.builder()
          .autoScalingGroupName("myasg-v000")
          .mixedInstancesPolicy(
            MixedInstancesPolicy.builder()
              .instancesDistribution(
                InstancesDistribution.builder()
                  .onDemandBaseCapacity(1)
                  .onDemandPercentageAboveBaseCapacity(50)
                  .spotMaxPrice("2")
                  .spotAllocationStrategy("capacity-optimized")
                  .build())
              .launchTemplate(
                software.amazon.awssdk.services.autoscaling.model.LaunchTemplate.builder()
                  .launchTemplateSpecification(
                    LaunchTemplateSpecification.builder()
                      .launchTemplateId(ec2Lt.launchTemplateId())
                      .version("\$Latest")
                      .build())
                  .overrides([
                    LaunchTemplateOverrides.builder().instanceType("some.type.large").weightedCapacity("2").build(),
                    LaunchTemplateOverrides.builder().instanceType("some.type.xlarge").weightedCapacity("4").build()])
                  .build())
              .build())
          .availabilityZones(az)
          .vpcZoneIdentifier(sb)
          .loadBalancerNames(["one", "two"])
          .targetGroupARNs(["tg1", "tg2"])
          .defaultCooldown(5)
          .healthCheckGracePeriod(5)
          .healthCheckType("elb")
          .terminationPolicies(["tp1", "tp2"])
          .createdTime(Instant.now())
          .build()
      ]).build()
    }
    1 * task.updateStatus('AWS_DEPLOY_TEST', deployMsg)

    where:
    sbTypeReq |  sbReq      |  sb     | azReq                      | az                         | deployMsg
    "internal"|["sb2","sb1"]|"sb1,sb2"| null                       | null                       |' > Deploying to subnetIds: sb2,sb1'
    "internal"|["sb2","sb1"]|"sb1,sb2"|["us-east-1b", "us-east-1a"]| null                       |' > Deploying to subnetIds: sb2,sb1'
    null     | null        | null    |["us-east-1b", "us-east-1a"]|["us-east-1a", "us-east-1b"]|'Deploying to availabilityZones: [us-east-1b, us-east-1a]'
  }

  void "throws duplicate exception if existing autoscaling group was created before safety window"() {
    given:
    def asgWithMipBuilder = new AsgWithMixedInstancesPolicyBuilder(ec2LtService, securityGroupService, deployDefaults,  autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.classicLoadBalancers = ["one", "two"]

    when:
    asgWithMipBuilder.build(task, taskPhase, asgName, asgConfig)

    then:
    thrown(AlreadyExistsException)
    1 * ec2LtService.createLaunchTemplate(asgConfig, asgName, _) >> ec2Lt
    1 * autoScaling.createAutoScalingGroup(_) >> { throw AlreadyExistsException.builder().message("Already exists, man").build() }
    1 * autoScaling.describeAutoScalingGroups(_) >> {
      DescribeAutoScalingGroupsResponse.builder().autoScalingGroups([
        AutoScalingGroup.builder()
          .autoScalingGroupName("myasg-v000")
          .mixedInstancesPolicy(mip)
          .loadBalancerNames(["one", "two"])
          .createdTime(Instant.now().minus(3, ChronoUnit.HOURS))
          .build()
      ]).build()
    }
  }

  @Unroll
  void "throws duplicate exception if existing and desired autoscaling group differ in configuration"() {
    given:
    def asgWithMipBuilder = new AsgWithMixedInstancesPolicyBuilder(ec2LtService, securityGroupService, deployDefaults,  autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.subnetType = sb == null ? null :"internal"
    asgConfig.subnetIds = sb == null ? null : ["sb2","sb1"]
    asgConfig.availabilityZones = azReq
    asgConfig.classicLoadBalancers = ["two", "one"]
    asgConfig.targetGroupArns = ["tg2", "tg1"]
    asgConfig.cooldown = 5
    asgConfig.healthCheckGracePeriod = 5
    asgConfig.healthCheckType = "ec2"
    asgConfig.terminationPolicies = ["tp2", "tp1"]

    when:
    asgWithMipBuilder.build(task, taskPhase, asgName, asgConfig)

    then:
    thrown(AlreadyExistsException)
    1 * ec2LtService.createLaunchTemplate(asgConfig, asgName, _) >> ec2Lt
    _ * amazonEC2.describeSubnets() >> DescribeSubnetsResponse.builder().subnets([Subnet.builder().subnetId('sb1').availabilityZone('az1').build(),Subnet.builder().subnetId('sb2').availabilityZone('az2').build(),]).build()
    1 * autoScaling.createAutoScalingGroup(_) >> { throw AlreadyExistsException.builder().message("Already exists, man").build() }
    1 * autoScaling.describeAutoScalingGroups(_) >> {
      DescribeAutoScalingGroupsResponse.builder().autoScalingGroups([
        AutoScalingGroup.builder()
          .autoScalingGroupName("myasg-v000")
          .mixedInstancesPolicy(
            MixedInstancesPolicy.builder()
              .instancesDistribution(
                InstancesDistribution.builder()
                  .onDemandBaseCapacity(1)
                  .onDemandPercentageAboveBaseCapacity(50)
                  .spotInstancePools(spotInstancePools)
                  .spotMaxPrice(spotMaxPrice)
                  .spotAllocationStrategy(spotAllocStrategy)
                  .build())
              .launchTemplate(
                software.amazon.awssdk.services.autoscaling.model.LaunchTemplate.builder()
                  .launchTemplateSpecification(
                    LaunchTemplateSpecification.builder()
                      .launchTemplateId(ec2LtId)
                      .version("\$Latest")
                      .build())
                  .overrides([
                    LaunchTemplateOverrides.builder().instanceType(override1InstType).weightedCapacity(override1Wgt as String).build(),
                    LaunchTemplateOverrides.builder().instanceType("some.type.xlarge").weightedCapacity("4").build()])
                  .build())
              .build())
          .availabilityZones(null)
          .vpcZoneIdentifier(sb)
          .loadBalancerNames(lb)
          .targetGroupARNs(tg)
          .defaultCooldown(cd)
          .healthCheckGracePeriod(hcGp)
          .healthCheckType(hc)
          .terminationPolicies(tp)
          .createdTime(Instant.now())
          .build()
      ]).build()
    }

    1 * task.updateStatus(taskPhase, "Deploying ASG $asgName with mixed instances policy " + mip.toString())
    1 * task.updateStatus(taskPhase, "$asgName already exists and does not seem to match desired state on: $failedPredicates")

    where:
    ec2LtId  | override1InstType | override1Wgt| spotAllocStrategy  | spotMaxPrice | spotInstancePools |  sb     |     azReq         | lb           | tg           |cd | hcGp |   hc   | tp           || failedPredicates
      "blah" |        "blah"     |      1      |        "blah"      |     "2"      |       null        |  "blah" |         []        | ["blah"]     |    ["blah"]  | 0 |   0  | "blah" | ["blah"]     || "health check type,target groups,mixed instances policy,health check grace period,cooldown,subnets,termination policies,load balancers"
      "blah" | "some.type.large" |      2      |"capacity-optimized"|     "2"      |       null        |"sb1,sb2"|       null        |["one","two"] |["tg1", "tg2"]| 5 |   5  | "ec2"  |["tp1", "tp2"]|| "mixed instances policy"
      "lt-1" |       "blah"      |      2      |"capacity-optimized"|     "2"      |       null        |"sb1,sb2"|       null        |["one","two"] |["tg1", "tg2"]| 5 |   5  | "ec2"  |["tp1", "tp2"]|| "mixed instances policy"
      "lt-1" | "some.type.large" |      3      |"capacity-optimized"|     "1.5"    |       null        |"sb1,sb2"|       null        |["one","two"] |["tg1", "tg2"]| 5 |   5  | "ec2"  |["tp1", "tp2"]|| "mixed instances policy"
      "lt-1" | "some.type.large" |      2      |   "lowest-price"   |     "2"      |          5        |"sb1,sb2"|       null        |["one","two"] |["tg1", "tg2"]| 5 |   5  | "ec2"  |["tp1", "tp2"]|| "mixed instances policy"
      "lt-1" | "some.type.large" |      2      |        "blah"      |     "2"      |       null        |"sb1,sb2"|       null        |["one","two"] |["tg1", "tg2"]| 5 |   5  | "ec2"  |["tp1", "tp2"]|| "mixed instances policy"
      "lt-1" | "some.type.large" |      2      |"capacity-optimized"|     "1"      |       null        |"sb1,sb2"|       null        |["one","two"] |["tg1", "tg2"]| 5 |   5  | "ec2"  |["tp1", "tp2"]|| "mixed instances policy"
      "lt-1" | "some.type.large" |      2      |"capacity-optimized"|     "2"      |       null        |  "blah" |       null        |["one","two"] |["tg1", "tg2"]| 5 |   5  | "ec2"  |["tp1", "tp2"]|| "subnets"
      "lt-1" | "some.type.large" |      2      |"capacity-optimized"|     "2"      |       null        |   null  |["az3","az2","az1"]|["one", "two"]|["tg1", "tg2"]| 5 |   5  | "ec2"  |["tp1", "tp2"]|| "availability zones"
      "lt-1" | "some.type.large" |      2      |"capacity-optimized"|     "2"      |       null        |"sb1,sb2"|   ["az2","az1"]   |   ["blah"]   |["tg1", "tg2"]| 5 |   5  | "ec2"  |["tp1", "tp2"]|| "load balancers"
      "lt-1" | "some.type.large" |      2      |"capacity-optimized"|     "2"      |       null        |"sb1,sb2"|   ["az2","az1"]   |["one", "two"]|    ["blah"]  | 5 |   5  | "ec2"  |["tp1", "tp2"]|| "target groups"
      "lt-1" | "some.type.large" |      2      |"capacity-optimized"|     "2"      |       null        |"sb1,sb2"|   ["az2","az1"]   |["one", "two"]|["tg1", "tg2"]| 0 |   5  | "ec2"  |["tp1", "tp2"]|| "cooldown"
      "lt-1" | "some.type.large" |      2      |"capacity-optimized"|     "2"      |       null        |"sb1,sb2"|   ["az2","az1"]   |["one", "two"]|["tg1", "tg2"]| 5 |   0  | "ec2"  |["tp1", "tp2"]|| "health check grace period"
      "lt-1" | "some.type.large" |      2      |"capacity-optimized"|     "2"      |       null        |"sb1,sb2"|   ["az2","az1"]   |["one", "two"]|["tg1", "tg2"]| 5 |   5  | "blah" |["tp1", "tp2"]|| "health check type"
      "lt-1" | "some.type.large" |      2      |"capacity-optimized"|     "2"      |       null        |"sb1,sb2"|       null        |["one", "two"]|["tg1", "tg2"]| 5 |   5  | "ec2"  |   ["blah"]   || "termination policies"
  }

  void "creates lifecycle hooks before scaling out asg"() {
    setup:
    def hooks = [getHook(), getHook()]
    def asgWithMipBuilder = new AsgWithMixedInstancesPolicyBuilder(ec2LtService, securityGroupService, deployDefaults,  autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.lifecycleHooks = hooks

    when:
    asgWithMipBuilder.build(task, taskPhase, asgName, asgConfig)

    then:
    1 * ec2LtService.createLaunchTemplate(asgConfig, asgName, _) >> ec2Lt
    1 * autoScaling.createAutoScalingGroup(_)
    1 * asgLifecycleHookWorker.attach(_, hooks, "myasg-v000")
    1 * autoScaling.updateAutoScalingGroup(*_)
  }

  def getHook() {
    new AmazonAsgLifecycleHook(
      name: "hook-name-" + new Random().nextInt(),
      roleARN: "role-rn",
      notificationTargetARN: "target-arn",
      notificationMetadata: null,
      lifecycleTransition: EC2InstanceLaunching,
      heartbeatTimeout: 300,
      defaultResult: CONTINUE
    )
  }

  void "should suspend auto scaling processes if specified"() {
    setup:
    def asgWithMipBuilder = new AsgWithMixedInstancesPolicyBuilder(ec2LtService, securityGroupService, deployDefaults,  autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.suspendedProcesses = ["Launch"]

    when:
    asgWithMipBuilder.build(task, taskPhase, asgName, asgConfig)

    then:
    1 * ec2LtService.createLaunchTemplate(asgConfig, asgName, _) >> ec2Lt
    1 * autoScaling.createAutoScalingGroup(_)
    1 * autoScaling.suspendProcesses(_)
    1 * autoScaling.updateAutoScalingGroup(*_)
  }

  @Unroll
  void "should enable capacity rebalance, if specified"() {
    given:
    def asgWithMipBuilder = new AsgWithMixedInstancesPolicyBuilder(ec2LtService, securityGroupService, deployDefaults,  autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.capacityRebalance = capacityRebalance

    when:
    def request = asgWithMipBuilder.buildRequest(task, taskPhase, asgName, asgConfig)

    then:
    1 * ec2LtService.createLaunchTemplate(asgConfig, asgName, _) >> ec2Lt
    request.capacityRebalance() == capacityRebalance

    where:
    capacityRebalance << [true, false, null]
  }
}
