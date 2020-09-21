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

package com.netflix.spinnaker.clouddriver.aws.deploy

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AlreadyExistsException
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.LaunchTemplate
import com.amazonaws.services.ec2.model.Subnet
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.model.AmazonAsgLifecycleHook
import com.netflix.spinnaker.clouddriver.aws.services.AsgService
import com.netflix.spinnaker.clouddriver.aws.services.LaunchTemplateService
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.model.Cluster
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant
import java.time.temporal.ChronoUnit

import static com.netflix.spinnaker.clouddriver.aws.model.AmazonAsgLifecycleHook.DefaultResult.CONTINUE
import static com.netflix.spinnaker.clouddriver.aws.model.AmazonAsgLifecycleHook.Transition.EC2InstanceLaunching

class AutoScalingWorkerUnitSpec extends Specification {

  @Autowired
  TaskRepository taskRepository

  def lcBuilder = Mock(LaunchConfigurationBuilder)
  def asgService = Mock(AsgService)
  def autoScaling = Mock(AmazonAutoScaling)
  def clusterProvider = Mock(ClusterProvider)
  def amazonEC2 = Mock(AmazonEC2)
  def asgLifecycleHookWorker = Mock(AsgLifecycleHookWorker)
  def dynamicConfigService = Mock(DynamicConfigService)
  def awsServerGroupNameResolver = new AWSServerGroupNameResolver('test', 'us-east-1', asgService, [clusterProvider])
  def credential = TestCredential.named('foo')
  def regionScopedProvider = Stub(RegionScopedProviderFactory.RegionScopedProvider) {
    getAutoScaling() >> autoScaling
    getLaunchConfigurationBuilder() >> lcBuilder
    getAsgService() >> asgService
    getAWSServerGroupNameResolver() >> awsServerGroupNameResolver
    getAmazonEC2() >> amazonEC2
    getAsgLifecycleHookWorker() >> asgLifecycleHookWorker
  }

  def setup() {
    Task task = new DefaultTask("task")
    TaskRepository.threadLocalTask.set(task)
  }

  @Unroll
  void "deploy workflow is create launch config, create asg"() {
    setup:
    def launchConfigName = "launchConfig"
    def mockAutoScalingWorker = Spy(AutoScalingWorker)
    mockAutoScalingWorker.application = "myasg"
    mockAutoScalingWorker.stack = "stack"
    mockAutoScalingWorker.freeFormDetails = "details"
    mockAutoScalingWorker.credentials = credential
    mockAutoScalingWorker.regionScopedProvider = regionScopedProvider
    mockAutoScalingWorker.sequence = sequence
    mockAutoScalingWorker.dynamicConfigService = dynamicConfigService

    when:
    mockAutoScalingWorker.deploy()

    then:
    1 * lcBuilder.buildLaunchConfiguration('myasg', null, _, null) >> launchConfigName
    1 * mockAutoScalingWorker.createAutoScalingGroup(expectedAsgName, launchConfigName, null) >> {}
    (sequence == null ? 1 : 0) * clusterProvider.getCluster('myasg', 'test', 'myasg-stack-details') >> { null }
    0 * clusterProvider._

    where:
    sequence || expectedAsgName
    null     || "myasg-stack-details-v000"
    0        || "myasg-stack-details-v000"
    1        || "myasg-stack-details-v001"
    11       || "myasg-stack-details-v011"
    111      || "myasg-stack-details-v111"
  }

  @Unroll
  void "deploy workflow is create launch template if enabled then create asg"() {
    setup:
    def mockAutoScalingWorker = Spy(AutoScalingWorker)
    mockAutoScalingWorker.application = "myasg"
    mockAutoScalingWorker.stack = "stack"
    mockAutoScalingWorker.freeFormDetails = "details"
    mockAutoScalingWorker.credentials = credential
    mockAutoScalingWorker.regionScopedProvider = regionScopedProvider
    mockAutoScalingWorker.sequence = sequence
    mockAutoScalingWorker.dynamicConfigService = dynamicConfigService

    and:
    mockAutoScalingWorker.setLaunchTemplate = true
    regionScopedProvider.getLaunchTemplateService() >> Mock(LaunchTemplateService) {
      createLaunchTemplate(_,_,_,_) >> new LaunchTemplate(launchTemplateId: "id", latestVersionNumber: 0, launchTemplateName: "lt")
    }

    when:
    mockAutoScalingWorker.deploy()

    then:
    1 * dynamicConfigService.isEnabled('aws.features.launch-templates', false) >> true
    1 * dynamicConfigService.getConfig(_, _, _) >> { "myasg" }
    1 * mockAutoScalingWorker.createAutoScalingGroup(expectedAsgName, null, { it.launchTemplateId == "id" }) >> {}
    (sequence == null ? 1 : 0) * clusterProvider.getCluster('myasg', 'test', 'myasg-stack-details') >> { null }
    0 * clusterProvider._

    where:
    sequence || expectedAsgName
    null     || "myasg-stack-details-v000"
    0        || "myasg-stack-details-v000"
    1        || "myasg-stack-details-v001"
    11       || "myasg-stack-details-v011"
    111      || "myasg-stack-details-v111"
  }

  void "deploy derives name from ancestor using launch templates and set ancestor name in the task result"() {
    setup:
    def launchTemplateService = Mock(LaunchTemplateService)
    def autoScalingWorker = new AutoScalingWorker(
      regionScopedProvider: regionScopedProvider,
      credentials: credential,
      application: "myasg",
      region: "us-east-1",
      setLaunchTemplate: true,
      dynamicConfigService: dynamicConfigService
    )

    and:
    regionScopedProvider.getLaunchTemplateService() >> launchTemplateService

    when:
    String asgName = autoScalingWorker.deploy()

    then:
    1 * dynamicConfigService.isEnabled('aws.features.launch-templates', false) >> true
    1 * dynamicConfigService.getConfig(_, _, _) >> { "myasg" }
    1 * launchTemplateService.createLaunchTemplate(_,_,_,_) >>
      new LaunchTemplate(launchTemplateId: "id", latestVersionNumber: 0, launchTemplateName: "lt")
    1 * clusterProvider.getCluster('myasg', 'test', 'myasg') >> {
      new Cluster.SimpleCluster(type: 'aws', serverGroups: [
        sG('myasg-v011', 0, 'us-east-1'), sG('myasg-v099', 1, 'us-west-1')
      ])
    }
    1 * asgService.getAutoScalingGroup('myasg-v011') >> { new AutoScalingGroup() }
    1 * asgService.getAutoScalingGroup('myasg-v012') >> { new AutoScalingGroup() }
    1 * asgService.getAutoScalingGroup('myasg-v013') >> { null }
    1 * autoScaling.createAutoScalingGroup(_)
    1 * autoScaling.updateAutoScalingGroup(_)
    0 * _

    asgName == 'myasg-v013'
    awsServerGroupNameResolver.getTask().resultObjects[0].ancestorServerGroupNameByRegion.get("us-east-1") == "myasg-v011"
  }

  void "deploy derives name from ancestor asg and sets the ancestor asg name in the task result"() {
    setup:
    def autoScalingWorker = new AutoScalingWorker(
      regionScopedProvider: regionScopedProvider,
      credentials: credential,
      application: "myasg",
      region: "us-east-1",
      dynamicConfigService: dynamicConfigService
    )

    when:
    String asgName = autoScalingWorker.deploy()

    then:
    1 * lcBuilder.buildLaunchConfiguration('myasg', null, _, null) >> 'lcName'
    1 * clusterProvider.getCluster('myasg', 'test', 'myasg') >> {
      new Cluster.SimpleCluster(type: 'aws', serverGroups: [
        sG('myasg-v011', 0, 'us-east-1'), sG('myasg-v099', 1, 'us-west-1')
      ])
    }
    1 * asgService.getAutoScalingGroup('myasg-v011') >> { new AutoScalingGroup() }
    1 * asgService.getAutoScalingGroup('myasg-v012') >> { new AutoScalingGroup() }
    1 * asgService.getAutoScalingGroup('myasg-v013') >> { null }
    1 * autoScaling.createAutoScalingGroup(_)
    1 * autoScaling.updateAutoScalingGroup(_)
    0 * _

    asgName == 'myasg-v013'
    awsServerGroupNameResolver.getTask().resultObjects[0].ancestorServerGroupNameByRegion.get("us-east-1") == "myasg-v011"
  }

  void "does not enable metrics collection when enabledMetrics are absent"() {
    setup:
    def autoScalingWorker = new AutoScalingWorker(
      enabledMetrics: [],
      instanceMonitoring: true,
      regionScopedProvider: regionScopedProvider,
      credentials: credential,
      application: "myasg",
      region: "us-east-1",
      dynamicConfigService: dynamicConfigService
    )

    when:
    String asgName = autoScalingWorker.deploy()

    then:
    0 * autoScaling.enableMetricsCollection(_)
  }

  void "creates lifecycle hooks before scaling out asg"() {
    setup:
    def hooks = [getHook(), getHook()]
    def autoScalingWorker = new AutoScalingWorker(
      enabledMetrics: [],
      instanceMonitoring: true,
      regionScopedProvider: regionScopedProvider,
      credentials: credential,
      application: "myasg",
      region: "us-east-1",
      lifecycleHooks: hooks,
      dynamicConfigService: dynamicConfigService
    )

    when:
    autoScalingWorker.deploy()

    then:
    1 * autoScaling.createAutoScalingGroup(_)
    then:
    1 * regionScopedProvider.getAsgLifecycleHookWorker().attach(_, hooks, "myasg-v000")
    then: "validate that scale out happens after lifecycle hooks are attached"
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

  void "does not enable metrics collection when instanceMonitoring is set to false"() {
    setup:
    def autoScalingWorker = new AutoScalingWorker(
      enabledMetrics: ['GroupMinSize', 'GroupMaxSize'],
      instanceMonitoring: false,
      regionScopedProvider: regionScopedProvider,
      credentials: credential,
      application: "myasg",
      region: "us-east-1",
      dynamicConfigService: dynamicConfigService
    )

    when:
    String asgName = autoScalingWorker.deploy()

    then:
    0 * autoScaling.enableMetricsCollection(_)
  }


  void "enables metrics collection for specified metrics when enabledMetrics are present"() {
    setup:
    def autoScalingWorker = new AutoScalingWorker(
      enabledMetrics: ['GroupMinSize', 'GroupMaxSize'],
      instanceMonitoring: true,
      regionScopedProvider: regionScopedProvider,
      credentials: credential,
      application: "myasg",
      region: "us-east-1",
      dynamicConfigService: dynamicConfigService
    )

    when:
    String asgName = autoScalingWorker.deploy()

    then:
    1 * autoScaling.enableMetricsCollection({ it.metrics == ['GroupMinSize', 'GroupMaxSize'] })
  }

  void "continues if serverGroup already exists, is reasonably the same and within safety window"() {
    setup:
    def autoScalingWorker = new AutoScalingWorker(
      enabledMetrics: ['GroupMinSize', 'GroupMaxSize'],
      instanceMonitoring: true,
      regionScopedProvider: regionScopedProvider,
      credentials: credential,
      application: "myasg",
      region: "us-east-1",
      classicLoadBalancers: ["one", "two"],
      dynamicConfigService: dynamicConfigService
    )

    when:
    String asgName = autoScalingWorker.deploy()

    then:
    noExceptionThrown()
    1 * lcBuilder.buildLaunchConfiguration('myasg', null, _, null) >> "myasg-12345"
    1 * autoScaling.createAutoScalingGroup(_) >> { throw new AlreadyExistsException("Already exists, man") }
    1 * autoScaling.describeAutoScalingGroups(_) >> {
      new DescribeAutoScalingGroupsResult(
        autoScalingGroups: [
          new AutoScalingGroup(
            autoScalingGroupName: "myasg-v000",
            launchConfigurationName: "myasg-12345",
            loadBalancerNames: ["one", "two"],
            createdTime: new Date()
          )
        ]
      )
    }
    asgName == "myasg-v000"
  }

  void "throws duplicate exception if existing autoscaling group was created before safety window"() {
    setup:
    def autoScalingWorker = new AutoScalingWorker(
      enabledMetrics: ['GroupMinSize', 'GroupMaxSize'],
      instanceMonitoring: true,
      regionScopedProvider: regionScopedProvider,
      credentials: credential,
      application: "myasg",
      region: "us-east-1",
      classicLoadBalancers: ["one", "two"],
      dynamicConfigService: dynamicConfigService
    )

    when:
    autoScalingWorker.deploy()

    then:
    thrown(AlreadyExistsException)
    1 * lcBuilder.buildLaunchConfiguration('myasg', null, _, null) >> "myasg-12345"
    _ * autoScaling.createAutoScalingGroup(_) >> { throw new AlreadyExistsException("Already exists, man") }
    _ * autoScaling.describeAutoScalingGroups(_) >> {
      new DescribeAutoScalingGroupsResult(
        autoScalingGroups: [
          new AutoScalingGroup(
            autoScalingGroupName: "myasg-v000",
            launchConfigurationName: "myasg-12345",
            loadBalancerNames: ["one", "two"],
            createdTime: new Date(Instant.now().minus(3, ChronoUnit.HOURS).toEpochMilli())
          )
        ]
      )
    }
  }

  void "throws duplicate exception if existing and desired autoscaling group differ settings"() {
    setup:
    def autoScalingWorker = new AutoScalingWorker(
      enabledMetrics: ['GroupMinSize', 'GroupMaxSize'],
      instanceMonitoring: true,
      regionScopedProvider: regionScopedProvider,
      credentials: credential,
      application: "myasg",
      region: "us-east-1",
      classicLoadBalancers: ["one", "two"],
      dynamicConfigService: dynamicConfigService
    )

    when:
    autoScalingWorker.deploy()

    then:
    thrown(AlreadyExistsException)
    1 * lcBuilder.buildLaunchConfiguration('myasg', null, _, null) >> "myasg-12345"
    _ * autoScaling.createAutoScalingGroup(_) >> { throw new AlreadyExistsException("Already exists, man") }
    _ * autoScaling.describeAutoScalingGroups(_) >> {
      new DescribeAutoScalingGroupsResult(
        autoScalingGroups: [
          new AutoScalingGroup(
            autoScalingGroupName: "myasg-v000",
            launchConfigurationName: "different",
            loadBalancerNames: ["three"],
            createdTime: new Date()
          )
        ]
      )
    }
  }

  @Unroll
  void "should validate provided subnet ids against those available for subnet type"() {
    given:
    def autoScalingWorker = new AutoScalingWorker(
      subnetIds: subnetIds
    )

    when:
    def filteredSubnetIds = autoScalingWorker.getSubnetIds(allSubnets)

    then:
    filteredSubnetIds == expectedSubnetIds

    when:
    autoScalingWorker = new AutoScalingWorker(
      subnetIds: ["invalid-subnet-id"]
    )
    autoScalingWorker.getSubnetIds(allSubnets)

    then:
    def e = thrown(IllegalStateException)
    e.message.startsWith("One or more subnet ids are not valid (invalidSubnetIds: invalid-subnet-id")

    where:
    subnetIds    | allSubnets                               || expectedSubnetIds
    ["subnet-1"] | [subnet("subnet-1"), subnet("subnet-2")] || ["subnet-1"]
    null         | [subnet("subnet-1"), subnet("subnet-2")] || ["subnet-1", "subnet-2"]
  }

  static Subnet subnet(String subnetId) {
    return new Subnet().withSubnetId(subnetId)
  }

  static ServerGroup sG(String name, Long createdTime, String region) {
    return new SimpleServerGroup(name: name, createdTime: createdTime, region: region)
  }
}
