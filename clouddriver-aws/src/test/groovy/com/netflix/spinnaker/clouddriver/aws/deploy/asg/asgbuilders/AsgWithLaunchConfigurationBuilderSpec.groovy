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

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AlreadyExistsException
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeSubnetsResult
import com.amazonaws.services.ec2.model.Subnet
import com.amazonaws.services.ec2.model.Tag
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AsgLifecycleHookWorker
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AutoScalingWorker.AsgConfiguration
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.LaunchConfigurationBuilder
import com.netflix.spinnaker.clouddriver.aws.model.AmazonAsgLifecycleHook
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataOverride
import com.netflix.spinnaker.clouddriver.data.task.Task
import org.apache.commons.lang3.StringUtils
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant
import java.time.temporal.ChronoUnit

import static com.netflix.spinnaker.clouddriver.aws.model.AmazonAsgLifecycleHook.DefaultResult.CONTINUE
import static com.netflix.spinnaker.clouddriver.aws.model.AmazonAsgLifecycleHook.Transition.EC2InstanceLaunching

class AsgWithLaunchConfigurationBuilderSpec extends Specification {

  def lcBuilder = Mock(LaunchConfigurationBuilder)
  def autoScaling = Mock(AmazonAutoScaling)
  def amazonEC2 = Mock(AmazonEC2)
  def asgLifecycleHookWorker = Mock(AsgLifecycleHookWorker)

  def credential = TestCredential.named('foo')
  def userDataOverride = Mock(UserDataOverride)
  def task = Mock(Task)
  def taskPhase = "AWS_DEPLOY_TEST"
  def asgConfig, asgName, launchConfigName

  def setup() {
    asgConfig = new AsgConfiguration(
      credentials: credential,
      application: "myasg",
      region: "us-east-1",
      minInstances: 1,
      maxInstances: 3,
      instanceType: "t1.test"
    )
    asgName = "myasg-v000"
    launchConfigName = "$asgName-20210119"
  }

  private LaunchConfigurationBuilder.LaunchConfigurationSettings getLcSettings(String asgName, AsgConfiguration cfg) {
    return LaunchConfigurationBuilder.LaunchConfigurationSettings.builder()
      .account(cfg.getCredentials().getName())
      .environment(cfg.getCredentials().getEnvironment())
      .accountType(cfg.getCredentials().getAccountType())
      .region(cfg.getRegion())
      .baseName(asgName)
      .suffix(null)
      .ami(cfg.getAmi())
      .iamRole(cfg.getIamRole())
      .classicLinkVpcId(cfg.getClassicLinkVpcId())
      .classicLinkVpcSecurityGroups(cfg.getClassicLinkVpcSecurityGroups())
      .instanceType(cfg.getInstanceType())
      .keyPair(cfg.getKeyPair())
      .base64UserData(cfg.getBase64UserData())
      .associatePublicIpAddress(cfg.getAssociatePublicIpAddress())
      .kernelId(cfg.getKernelId())
      .ramdiskId(cfg.getRamdiskId())
      .ebsOptimized(cfg.getEbsOptimized() != null ? cfg.getEbsOptimized() : false)
      .spotPrice(cfg.getSpotMaxPrice())
      .instanceMonitoring(cfg.getInstanceMonitoring() != null ? cfg.getInstanceMonitoring() : false)
      .blockDevices(cfg.getBlockDevices())
      .securityGroups(cfg.getSecurityGroups())
      .build()
  }

  private DescribeSubnetsResult getDescribeSubnetsResult() {
    return new DescribeSubnetsResult(subnets: [
      new Subnet(subnetId: 'subnetId1', availabilityZone: 'us-east-1a', tags: [new Tag(key: 'immutable_metadata', value: '{"purpose": "internal", "target": "ec2" }')]),
      new Subnet(subnetId: 'subnetId2', availabilityZone: 'us-west-2a'),
      new Subnet(subnetId: 'subnetId3', availabilityZone: 'us-west-2a'),
    ])
  }

  void "should build ASG request with launch config correctly"() {
    given:
    def asgWithLcBuilder = new AsgWithLaunchConfigurationBuilder(lcBuilder, autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.availabilityZones = ["us-east-1a"]
    asgConfig.subnetType = "internal"
    asgConfig.legacyUdf = false
    asgConfig.desiredInstances = 2
    asgConfig.spotMaxPrice = 0.5
    asgConfig.classicLoadBalancers = ["one", "two"]
    asgConfig.targetGroupArns = ["tg1", "tg2"]
    asgConfig.cooldown = 5
    asgConfig.healthCheckGracePeriod = 5
    asgConfig.healthCheckType = "ec2"
    asgConfig.terminationPolicies = ["Default", "OldestInstance"]
    asgConfig.userDataOverride = userDataOverride
    asgConfig.ebsOptimized = true
    asgConfig.securityGroups = ["mysg"]
    def settings = getLcSettings(asgName, asgConfig)

    when:
    def request = asgWithLcBuilder.buildRequest(task, taskPhase, asgName, asgConfig)

    then:
    1 * lcBuilder.buildLaunchConfiguration("myasg", "internal", settings, false, userDataOverride) >> launchConfigName
    2 * amazonEC2.describeSubnets() >> getDescribeSubnetsResult()

    and:
    request.getLaunchConfigurationName() == launchConfigName
    request.getAutoScalingGroupName() == asgName
    request.getMinSize() == 1
    request.getMaxSize() == 3
    request.getDesiredCapacity() == 2
    request.getLoadBalancerNames() == ["one", "two"]
    request.getTargetGroupARNs() == ["tg1", "tg2"]
    request.getDefaultCooldown() == 5
    request.getHealthCheckGracePeriod() == 5
    request.getHealthCheckType() == "ec2"
    request.getTerminationPolicies() == ["Default", "OldestInstance"]
  }

  void "should build ASG request with tags correctly"() {
    given:
    def asgWithLcBuilder = new AsgWithLaunchConfigurationBuilder(lcBuilder, autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.tags = [foo: "bar"]
    def settings = getLcSettings(asgName, asgConfig)

    when:
    def request = asgWithLcBuilder.buildRequest(task, taskPhase, asgName, asgConfig)

    then:
    1 * lcBuilder.buildLaunchConfiguration("myasg", null, settings, null, null) >> launchConfigName
    request.getLaunchConfigurationName() == launchConfigName
    def tag = request.getTags()[0]
    tag.getKey() == "foo"
    tag.getValue() == "bar"
    tag.getPropagateAtLaunch() == true
  }

  @Unroll
  void "should favor subnetIds over AZ while building ASG request"() {
    given:
    def asgWithLcBuilder = new AsgWithLaunchConfigurationBuilder(lcBuilder, autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.availabilityZones = ["us-west-2a"]
    asgConfig.subnetIds = subnetIds
    asgConfig.subnetType = subnetType
    def settings = getLcSettings(asgName, asgConfig)

    when:
    def request = asgWithLcBuilder.buildRequest(task, taskPhase, asgName, asgConfig)

    then:
    1 * lcBuilder.buildLaunchConfiguration("myasg", subnetType, settings, null, null) >> launchConfigName
    (StringUtils.isEmpty(subnetType) ? 0 : 2) * amazonEC2.describeSubnets() >> getDescribeSubnetsResult()
    1 * task.updateStatus(taskPhase, "Deploying ASG $asgName with launch configuration $launchConfigName")
    1 * task.updateStatus(taskPhase, deployMsg)
    0 * _

    and:
    request.getLaunchConfigurationName() == launchConfigName
    request.getVPCZoneIdentifier() == subnetIdsForAsg
    subnetIdsForAsg == null
      ? request.getAvailabilityZones() == ["us-west-2a"]
      : request.getAvailabilityZones() == []

    where:
    subnetType |  subnetIds   | subnetIdsForAsg  ||  deployMsg
    "internal" |["subnetId3"] |  "subnetId3"     || " > Deploying to subnetIds: subnetId3"
    "internal" |["subnetId2"] |  "subnetId2"     || " > Deploying to subnetIds: subnetId2"
    null       |    null      |        null      || "Deploying to availabilityZones: [us-west-2a]"
    null       |      []      |        null      || "Deploying to availabilityZones: [us-west-2a]"
  }

  @Unroll
  void "should filter and validate subnets by AZ while building ASG request"() {
    given:
    def asgWithLcBuilder = new AsgWithLaunchConfigurationBuilder(lcBuilder, autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.availabilityZones = ["us-east-1a"]
    asgConfig.subnetIds = subnetIds
    asgConfig.subnetType = "internal"
    def settings = getLcSettings(asgName, asgConfig)

    when:
    def request = asgWithLcBuilder.buildRequest(task, taskPhase, asgName, asgConfig)

    then:
    1 * lcBuilder.buildLaunchConfiguration("myasg", "internal", settings, null, null) >> launchConfigName
    2 * amazonEC2.describeSubnets() >> getDescribeSubnetsResult()
    1 * task.updateStatus(taskPhase, "Deploying ASG $asgName with launch configuration $launchConfigName")
    1 * task.updateStatus(taskPhase, " > Deploying to subnetIds: subnetId1")
    0 * _

    and:
    request.getLaunchConfigurationName() == launchConfigName
    request.getVPCZoneIdentifier() == subnetIdsForAsg
    request.getAvailabilityZones() == []

    where:
    subnetIds     |   subnetIdsForAsg
        []        |  "subnetId1"   // filter subnets by AZ
    ["subnetId1"] |  "subnetId1"   // validate subnets by AZ
  }

  @Unroll
  void "throws exception when invalid subnet IDs are specified while building ASG request"() {
    given:
    def asgWithLcBuilder = new AsgWithLaunchConfigurationBuilder(lcBuilder, autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.availabilityZones = availabilityZones
    asgConfig.subnetIds = subnetIds
    asgConfig.subnetType = subnetType
    def settings = getLcSettings(asgName, asgConfig)

    when:
    asgWithLcBuilder.buildRequest(task, taskPhase, asgName, asgConfig)

    then:
    1 * lcBuilder.buildLaunchConfiguration("myasg", subnetType, settings, null, null) >> launchConfigName
    (StringUtils.isEmpty(subnetType) ? 0 : 1) * amazonEC2.describeSubnets() >> getDescribeSubnetsResult()
    1 * task.updateStatus(taskPhase, "Deploying ASG $asgName with launch configuration $launchConfigName")
    0 * _

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
    def asgWithLcBuilder = new AsgWithLaunchConfigurationBuilder(lcBuilder, autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.availabilityZones = []
    asgConfig.subnetType = "internal"
    asgConfig.subnetIds = subnetIds
    def settings = getLcSettings(asgName, asgConfig)

    when:
    def request = asgWithLcBuilder.buildRequest(task, taskPhase, asgName, asgConfig)

    then:
    1 * lcBuilder.buildLaunchConfiguration("myasg", "internal", settings, null, null) >> launchConfigName
    2 * amazonEC2.describeSubnets() >> getDescribeSubnetsResult()
    1 * task.updateStatus(taskPhase, "Deploying ASG $asgName with launch configuration $launchConfigName")
    1 * task.updateStatus(taskPhase, deployMsg)
    0 * _

    and:
    request.getLaunchConfigurationName() == launchConfigName
    request.getVPCZoneIdentifier() == subnetIdsForAsg
    request.getAvailabilityZones() == []

    where:
           subnetIds         |  subnetIdsForAsg       ||  deployMsg
              []             |   "subnetId1"          || " > Deploying to subnetIds: subnetId1"  // filtered by subnet purpose tags
          ["subnetId2"]      |  "subnetId2"           || " > Deploying to subnetIds: subnetId2"  // not filtered by subnet purpose tags
   ["subnetId1","subnetId2"] |  "subnetId1,subnetId2" || " > Deploying to subnetIds: subnetId1,subnetId2" // not filtered by subnet purpose tags
  }

  @Unroll
  void "throws exception when subnetIds are not specified and no suitable subnet found for subnet purpose while building ASG request"() {
    given:
    def asgWithLcBuilder = new AsgWithLaunchConfigurationBuilder(lcBuilder, autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.subnetIds = []
    asgConfig.availabilityZones = availabilityZones
    asgConfig.subnetType = subnetType
    def settings = getLcSettings(asgName, asgConfig)

    when:
    asgWithLcBuilder.buildRequest(task, taskPhase, asgName, asgConfig)

    then:
    1 * lcBuilder.buildLaunchConfiguration("myasg", subnetType, settings, null, null) >> launchConfigName
    2 * amazonEC2.describeSubnets() >> getDescribeSubnetsResult()
    1 * task.updateStatus(taskPhase, "Deploying ASG $asgName with launch configuration $launchConfigName")
    0 * _

    and:
    def ex = thrown(RuntimeException)
    ex.getMessage() == "No suitable subnet was found for internal subnet purpose '$subnetType'!"

    where:
    subnetType   | availabilityZones
    "internal"   | ["eu-central-1a"]
    "internal"   | ["us-west-2a"]
    "unknown"    | ["us-west-1b"]
  }

  void "should build ASG with launch config correctly"() {
    given:
    def asgWithLcBuilder = new AsgWithLaunchConfigurationBuilder(lcBuilder, autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.availabilityZones = ["us-east-1a"]
    asgConfig.subnetType = "internal"
    asgConfig.legacyUdf = false
    asgConfig.desiredInstances = 2
    asgConfig.spotMaxPrice = 0.5
    asgConfig.classicLoadBalancers = ["one", "two"]
    asgConfig.targetGroupArns = ["tg1", "tg2"]
    asgConfig.cooldown = 5
    asgConfig.healthCheckGracePeriod = 5
    asgConfig.healthCheckType = "ec2"
    asgConfig.terminationPolicies = ["Default", "OldestInstance"]
    asgConfig.userDataOverride = userDataOverride
    asgConfig.ebsOptimized = true
    asgConfig.securityGroups = ["mysg"]
    def settings = getLcSettings(asgName, asgConfig)

    when:
    def asgNameRes = asgWithLcBuilder.build(task, taskPhase, asgName, asgConfig)

    then:
    asgNameRes == asgName
    1 * lcBuilder.buildLaunchConfiguration("myasg", "internal", settings, false, userDataOverride) >> launchConfigName
    2 * amazonEC2.describeSubnets() >> getDescribeSubnetsResult()
    1 * autoScaling.createAutoScalingGroup(_)
    1 * autoScaling.updateAutoScalingGroup(_)
    1 * task.updateStatus(taskPhase, "Deploying ASG $asgName with launch configuration $launchConfigName")
    1 * task.updateStatus(taskPhase, ' > Deploying to subnetIds: subnetId1')
    1 * task.updateStatus(taskPhase, 'Setting size of myasg-v000 in foo/us-east-1 to [min=1, max=3, desired=2]')
    1 * task.updateStatus(taskPhase, "Deployed EC2 server group named $asgName")
    0 * _
  }

  @Unroll
  void "does not enable metrics collection when enabledMetrics are absent or instanceMonitoring is falsy"() {
    setup:
    def asgWithLcBuilder = new AsgWithLaunchConfigurationBuilder(lcBuilder, autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.enabledMetrics = enabledMetrics
    asgConfig.instanceMonitoring = instanceMonitoring

    when:
    asgWithLcBuilder.build(task, taskPhase, asgName, asgConfig)

    then:
    count * autoScaling.enableMetricsCollection({count == 1 ? it.metrics == ['GroupMinSize', 'GroupMaxSize'] : _ })

    where:
    enabledMetrics                   | instanceMonitoring  | count
            null                     | null                |  0
            []                       | null                |  0
            []                       | false               |  0
            []                       | true                |  0
    ['GroupMinSize', 'GroupMaxSize'] | null                |  0
    ['GroupMinSize', 'GroupMaxSize'] | []                  |  0
    ['GroupMinSize', 'GroupMaxSize'] | false               |  0
    ['GroupMinSize', 'GroupMaxSize'] | true                |  1
  }

  void "continues if serverGroup already exists, is reasonably the same and within safety window"() {
    given:
    def asgWithLcBuilder = new AsgWithLaunchConfigurationBuilder(lcBuilder, autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.classicLoadBalancers = ["one", "two"]

    when:
    asgWithLcBuilder.build(task, taskPhase, asgName, asgConfig)

    then:
    noExceptionThrown()
    1 * lcBuilder.buildLaunchConfiguration("myasg", null, _, null, null) >> launchConfigName
    1 * autoScaling.createAutoScalingGroup(_) >> { throw new AlreadyExistsException("Already exists, man") }
    1 * autoScaling.describeAutoScalingGroups(_) >> {
      new DescribeAutoScalingGroupsResult(
        autoScalingGroups: [
          new AutoScalingGroup(
            autoScalingGroupName: "myasg-v000",
            launchConfigurationName: launchConfigName,
            loadBalancerNames: ["one", "two"],
            createdTime: new Date()
          )
        ]
      )
    }
  }

  @Unroll
  void "continues if serverGroup already exists, and existing and desired autoscaling group have the same configuration"() {
    given:
    def asgWithLcBuilder = new AsgWithLaunchConfigurationBuilder(lcBuilder, autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.subnetType = sbTypeReq
    asgConfig.subnetIds = sbReq
    asgConfig.availabilityZones = azReq
    asgConfig.legacyUdf = false
    asgConfig.desiredInstances = 2
    asgConfig.spotMaxPrice = 0.5
    asgConfig.classicLoadBalancers = ["two", "one"]
    asgConfig.targetGroupArns = ["tg2", "tg1"]
    asgConfig.cooldown = 5
    asgConfig.healthCheckGracePeriod = 5
    asgConfig.healthCheckType = "ec2"
    asgConfig.terminationPolicies = ["tp2", "tp1"]

    when:
    asgWithLcBuilder.build(task, taskPhase, asgName, asgConfig)

    then:
    noExceptionThrown()
    1 * lcBuilder.buildLaunchConfiguration("myasg", asgConfig.subnetType, _, false, null) >> launchConfigName
    if (sbTypeReq != null) {
      2 * amazonEC2.describeSubnets() >> new DescribeSubnetsResult(subnets: [new Subnet(subnetId: 'sb1', availabilityZone: 'us-east-1a'), new Subnet(subnetId: 'sb2', availabilityZone: 'us-east-1b'),])
    }
    1 * autoScaling.createAutoScalingGroup(_) >> { throw new AlreadyExistsException("Already exists, man") }
    1 * autoScaling.describeAutoScalingGroups(_) >> {
      new DescribeAutoScalingGroupsResult(
        autoScalingGroups: [
          new AutoScalingGroup(
            autoScalingGroupName: "myasg-v000",
            launchConfigurationName: launchConfigName,
            availabilityZones: az,
            vPCZoneIdentifier: sb,
            loadBalancerNames: ["one", "two"],
            targetGroupARNs: ["tg1", "tg2"],
            defaultCooldown: 5,
            healthCheckGracePeriod: 5,
            healthCheckType: "ec2",
            terminationPolicies: ["tp1", "tp2"],
            createdTime: new Date()
          )
        ]
      )
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
    def asgWithLcBuilder = new AsgWithLaunchConfigurationBuilder(lcBuilder, autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.classicLoadBalancers = ["one", "two"]

    when:
    asgWithLcBuilder.build(task, taskPhase, asgName, asgConfig)

    then:
    thrown(AlreadyExistsException)
    1 * lcBuilder.buildLaunchConfiguration("myasg", null, _, null, null) >> launchConfigName
    1 * autoScaling.createAutoScalingGroup(_) >> { throw new AlreadyExistsException("Already exists, man") }
    1 * autoScaling.describeAutoScalingGroups(_) >> {
      new DescribeAutoScalingGroupsResult(
        autoScalingGroups: [
          new AutoScalingGroup(
            autoScalingGroupName: "myasg-v000",
            launchConfigurationName: launchConfigName,
            loadBalancerNames: ["one", "two"],
            createdTime: new Date(Instant.now().minus(3, ChronoUnit.HOURS).toEpochMilli())
          )
        ]
      )
    }
  }

  @Unroll
  void "throws duplicate exception if existing and desired autoscaling group differ in configuration"() {
    given:
    def asgWithLcBuilder = new AsgWithLaunchConfigurationBuilder(lcBuilder, autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.subnetType = sb == null ? null :"internal"
    asgConfig.subnetIds = sb == null ? null : ["sb2","sb1"]
    asgConfig.availabilityZones = azReq
    asgConfig.legacyUdf = false
    asgConfig.desiredInstances = 2
    asgConfig.spotMaxPrice = 0.5
    asgConfig.classicLoadBalancers = ["two", "one"]
    asgConfig.targetGroupArns = ["tg2", "tg1"]
    asgConfig.cooldown = 5
    asgConfig.healthCheckGracePeriod = 5
    asgConfig.healthCheckType = "ec2"
    asgConfig.terminationPolicies = ["tp2", "tp1"]

    when:
    asgWithLcBuilder.build(task, taskPhase, asgName, asgConfig)

    then:
    thrown(AlreadyExistsException)
    1 * lcBuilder.buildLaunchConfiguration("myasg", asgConfig.subnetType, _, false, null) >> launchConfigName
    _ * amazonEC2.describeSubnets() >> new DescribeSubnetsResult(subnets: [new Subnet(subnetId: 'sb1', availabilityZone: 'az1'),new Subnet(subnetId: 'sb2', availabilityZone: 'az2'),])
    1 * autoScaling.createAutoScalingGroup(_) >> { throw new AlreadyExistsException("Already exists, man") }
    1 * autoScaling.describeAutoScalingGroups(_) >> {
      new DescribeAutoScalingGroupsResult(
        autoScalingGroups: [
          new AutoScalingGroup(
            autoScalingGroupName: "myasg-v000",
            launchConfigurationName: lc,
            availabilityZones: null,
            vPCZoneIdentifier: sb,
            loadBalancerNames: lb,
            targetGroupARNs: tg,
            defaultCooldown: cd,
            healthCheckGracePeriod: hcGp,
            healthCheckType: hc,
            terminationPolicies: tp,
            createdTime: new Date()
          )
        ]
      )
    }
    1 * task.updateStatus('AWS_DEPLOY_TEST', "Deploying ASG myasg-v000 with launch configuration $launchConfigName")
    1 * task.updateStatus(taskPhase, "$asgName already exists and does not seem to match desired state on: $failedPredicates")

    where:
    lc                    |   sb    |     azReq         | lb           | tg           |cd | hcGp |   hc   | tp           || failedPredicates
          "blah"          | "blah"  |       []          | ["blah"]     |    ["blah"]  | 0 |   0  | "blah" | ["blah"]     || "health check type,target groups,health check grace period,cooldown,subnets,termination policies,launch configuration,load balancers"
          "blah"          |"sb1,sb2"|       null        |["one","two"] |["tg1", "tg2"]| 5 |   5  | "ec2"  |["tp1", "tp2"]|| "launch configuration"
    "myasg-v000-20210119" |  "blah" |       null        |["one","two"] |["tg1", "tg2"]| 5 |   5  | "ec2"  |["tp1", "tp2"]|| "subnets"
    "myasg-v000-20210119" |   null  |["az3","az2","az1"]|["one", "two"]|["tg1", "tg2"]| 5 |   5  | "ec2"  |["tp1", "tp2"]|| "availability zones"
    "myasg-v000-20210119" |"sb1,sb2"|   ["az2","az1"]   |   ["blah"]   |["tg1", "tg2"]| 5 |   5  | "ec2"  |["tp1", "tp2"]|| "load balancers"
    "myasg-v000-20210119" |"sb1,sb2"|   ["az2","az1"]   |["one", "two"]|    ["blah"]  | 5 |   5  | "ec2"  |["tp1", "tp2"]|| "target groups"
    "myasg-v000-20210119" |"sb1,sb2"|   ["az2","az1"]   |["one", "two"]|["tg1", "tg2"]| 0 |   5  | "ec2"  |["tp1", "tp2"]|| "cooldown"
    "myasg-v000-20210119" |"sb1,sb2"|   ["az2","az1"]   |["one", "two"]|["tg1", "tg2"]| 5 |   0  | "ec2"  |["tp1", "tp2"]|| "health check grace period"
    "myasg-v000-20210119" |"sb1,sb2"|   ["az2","az1"]   |["one", "two"]|["tg1", "tg2"]| 5 |   5  | "blah" |["tp1", "tp2"]|| "health check type"
    "myasg-v000-20210119" |"sb1,sb2"|       null        |["one", "two"]|["tg1", "tg2"]| 5 |   5  | "ec2"  |   ["blah"]   || "termination policies"
 }

  void "creates lifecycle hooks before scaling out asg"() {
    setup:
    def hooks = [getHook(), getHook()]
    def asgWithLcBuilder = new AsgWithLaunchConfigurationBuilder(lcBuilder, autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.lifecycleHooks = hooks

    when:
    asgWithLcBuilder.build(task, taskPhase, asgName, asgConfig)

    then:
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
    def asgWithLcBuilder = new AsgWithLaunchConfigurationBuilder(lcBuilder, autoScaling, amazonEC2, asgLifecycleHookWorker)
    asgConfig.suspendedProcesses = ["Launch"]

    when:
    asgWithLcBuilder.build(task, taskPhase, asgName, asgConfig)

    then:
    1 * autoScaling.createAutoScalingGroup(_)
    1 * autoScaling.suspendProcesses(_)
    1 * autoScaling.updateAutoScalingGroup(*_)
  }
}
