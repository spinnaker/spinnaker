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


package com.netflix.spinnaker.kato.aws.deploy

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeSubnetsResult
import com.amazonaws.services.ec2.model.Subnet
import com.amazonaws.services.ec2.model.Tag
import com.netflix.spinnaker.kato.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.aws.services.SecurityGroupService
import spock.lang.Specification
import spock.lang.Unroll

class AutoScalingWorkerUnitSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "deploy workflow is create security group, create launch config, create asg"() {
    setup:
    def asgName = "myasg-v000"
    def launchConfigName = "launchConfig"
    def mockAutoScalingWorker = Spy(AutoScalingWorker)
    mockAutoScalingWorker.application = "myasg"
    mockAutoScalingWorker.securityGroupService = Mock(SecurityGroupService) {
      1 * getSecurityGroupForApplication("myasg", null) >> "sg-1234"
    }

    when:
    mockAutoScalingWorker.deploy()

    then:
    1 * mockAutoScalingWorker.getAncestorAsg() >> null
    1 * mockAutoScalingWorker.getAutoScalingGroupName(0) >> asgName
    1 * mockAutoScalingWorker.getUserData(asgName, launchConfigName) >> { "" }
    1 * mockAutoScalingWorker.getLaunchConfigurationName(0) >> launchConfigName
    1 * mockAutoScalingWorker.createLaunchConfiguration(_, _, ['sg-1234']) >> { launchConfigName }
    1 * mockAutoScalingWorker.createAutoScalingGroup(_, _) >> {}
  }

  void "deploy favors security groups of ancestor asg"() {
    setup:
    def mockAutoScalingWorker = Spy(AutoScalingWorker)
    mockAutoScalingWorker.application = "myasg"
    mockAutoScalingWorker.securityGroupService = Mock(SecurityGroupService) {
      1 * getSecurityGroupForApplication("myasg", null) >> "sg-1234"
    }

    when:
    mockAutoScalingWorker.deploy()

    then:
    1 * mockAutoScalingWorker.getUserData(_, _) >> null
    1 * mockAutoScalingWorker.getLaunchConfigurationName(_) >> "launchConfigName"
    1 * mockAutoScalingWorker.getAncestorAsg() >> {
      [autoScalingGroupName: "asgard-test-v000", launchConfigurationName: "asgard-test-v000-launchConfigName"]
    }
    1 * mockAutoScalingWorker.createLaunchConfiguration("launchConfigName", null, _) >> {
      'launchConfigName'
    }
    1 * mockAutoScalingWorker.createAutoScalingGroup(_, _) >> {}
  }

  void "security group is created for app if one is not found"() {
    setup:
    def mockAutoScalingWorker = Spy(AutoScalingWorker)
    mockAutoScalingWorker.application = "myasg"
    mockAutoScalingWorker.securityGroupService = Mock(SecurityGroupService) {
      1 * getSecurityGroupForApplication("myasg", null)
      1 * createSecurityGroup("myasg", null) >> "sg-1234"
    }

    when:
    mockAutoScalingWorker.deploy()

    then:
    1 * mockAutoScalingWorker.getUserData(_, _) >> null
    1 * mockAutoScalingWorker.getLaunchConfigurationName(_) >> "launchConfigName"

    1 * mockAutoScalingWorker.getAncestorAsg() >> null
    1 * mockAutoScalingWorker.createLaunchConfiguration("launchConfigName", null, ['sg-1234']) >> { "launchConfigName" }
    1 * mockAutoScalingWorker.createAutoScalingGroup(_, _) >> {}
  }

  void "when explicitly provided security group are used and a per application group is not created"() {
    setup:
    def mockAutoScalingWorker = Spy(AutoScalingWorker)
    mockAutoScalingWorker.application = "myasg"
    mockAutoScalingWorker.securityGroups = ['sg-1234', 'sg-2345']
    mockAutoScalingWorker.securityGroupService = Mock(SecurityGroupService) {
      0 * getSecurityGroupForApplication("myasg", null)
      0 * createSecurityGroup("myasg", null)
    }

    when:
    mockAutoScalingWorker.deploy()

    then:
    1 * mockAutoScalingWorker.getUserData(_, _) >> null
    1 * mockAutoScalingWorker.getLaunchConfigurationName(_) >> "launchConfigName"

    1 * mockAutoScalingWorker.getAncestorAsg() >> null
    1 * mockAutoScalingWorker.createLaunchConfiguration("launchConfigName", null, ['sg-1234', 'sg-2345']) >> { "launchConfigName" }
    1 * mockAutoScalingWorker.createAutoScalingGroup(_, _) >> {}
  }

  void "subnet ids are retrieved when type is specified"() {
    setup:
    def ec2 = Mock(AmazonEC2)
    ec2.describeSubnets() >> {
      def mock = Mock(DescribeSubnetsResult)
      mock.getSubnets() >> [new Subnet()
                              .withSubnetId("123")
                              .withState("available")
                              .withAvailabilityZone("us-west-1a")
                              .withTags([new Tag("immutable_metadata", '{ "purpose": "internal", "target": "ec2"}')])
      ]
      mock
    }
    def worker = new AutoScalingWorker(amazonEC2: ec2, subnetType: "internal",
      availabilityZones: ["us-west-1a"])

    when:
    def results = worker.subnetIds

    then:
    results.first() == "123"
  }

  void "block device mappings are applied when available"() {
    setup:
    def autoscaling = Mock(AmazonAutoScaling)
    def worker = new AutoScalingWorker(autoScaling: autoscaling, blockDevices: [new AmazonBlockDevice(deviceName: '/dev/sdb', virtualName: 'ephemeral1'), new AmazonBlockDevice(deviceName: "/dev/sdc", size: 125, iops: 100, deleteOnTermination: false, volumeType: 'io1', snapshotId: 's-69')])

    when:
    worker.createLaunchConfiguration(null, null, null)

    then:
    1 * autoscaling.createLaunchConfiguration(_) >> { CreateLaunchConfigurationRequest request ->
      assert request.blockDeviceMappings.size() == 2
      request.blockDeviceMappings.first().with {
          assert deviceName == "/dev/sdb"
          assert virtualName == 'ephemeral1'
          assert ebs == null
      }
      request.blockDeviceMappings.last().with {
          assert deviceName == '/dev/sdc'
          assert virtualName == null
          assert ebs.snapshotId == 's-69'
          assert ebs.volumeType == 'io1'
          assert ebs.deleteOnTermination == false
          assert ebs.iops == 100
          assert ebs.volumeSize == 125
      }
    }
  }

  void "should fail for invalid characters in the asg name"() {
    given:
    def worker = new AutoScalingWorker(application: "foo", stack: "bar", freeFormDetails: "east!")

    when:
    worker.getAutoScalingGroupName(1)

    then:
    IllegalArgumentException e = thrown()
    e.message == "(Use alphanumeric characters only)"
  }

  void "application, stack, and freeform details make up the asg name"() {
    given:
    def worker = new AutoScalingWorker(application: "foo", stack: "bar", freeFormDetails: "east")

    expect:
    worker.getAutoScalingGroupName(1) == "foo-bar-east-v001"
  }

  void "push sequence should be ignored when specified so"() {
    given:
    def worker = new AutoScalingWorker(application: "foo", stack: "bar", freeFormDetails: "east", ignoreSequence: true)

    expect:
    worker.getAutoScalingGroupName(0) == "foo-bar-east"
  }

  void "application, and stack make up the asg name"() {
    given:
    def worker = new AutoScalingWorker(application: "foo", stack: "bar")

    expect:
    worker.getAutoScalingGroupName(1) == "foo-bar-v001"
  }

  void "application and version make up the asg name"() {
    given:
    def worker = new AutoScalingWorker(application: "foo")

    expect:
    worker.getAutoScalingGroupName(1) == "foo-v001"
  }

  void "application, and freeform details make up the asg name"() {
    given:
    def worker = new AutoScalingWorker(application: "foo", freeFormDetails: "east")

    expect:
    worker.getAutoScalingGroupName(1) == "foo--east-v001"
  }

  void "should lookup security groups when appropriate"() {
    setup:
    def mockAutoScalingWorker = Spy(AutoScalingWorker)
    mockAutoScalingWorker.application = "myasg"
    mockAutoScalingWorker.securityGroups = ["sg-12345", "mysecurityGroup"]
    mockAutoScalingWorker.securityGroupService = Mock(SecurityGroupService) {
      1 * getSecurityGroupIds(["mysecurityGroup"]) >> ["mysecurityGroup": "sg-0000"]
    }

    when:
    mockAutoScalingWorker.deploy()

    then:
    1 * mockAutoScalingWorker.getUserData(_, _) >> null
    1 * mockAutoScalingWorker.getLaunchConfigurationName(_) >> "launchConfigName"

    1 * mockAutoScalingWorker.getAncestorAsg() >> null
    1 * mockAutoScalingWorker.createLaunchConfiguration("launchConfigName", null, ['sg-12345', 'sg-0000']) >> { "launchConfigName" }
    1 * mockAutoScalingWorker.createAutoScalingGroup(_, _) >> {}
  }

  @Unroll
  void "should consider app, stack, and details in determining ancestor ASG"() {
    setup:
    def autoScaling = Mock(AmazonAutoScaling)
    AutoScalingWorker worker = new AutoScalingWorker(
      autoScaling: autoScaling,
      application: 'app',
      stack: stack,
      freeFormDetails: freeFormDetails
    )

    when:
    AutoScalingGroup ancestor = worker.ancestorAsg

    then:
    ancestor?.autoScalingGroupName == expected
    1 * autoScaling.describeAutoScalingGroups(_) >> new DescribeAutoScalingGroupsResult(
      autoScalingGroups: [
        new AutoScalingGroup(autoScalingGroupName: 'app-v001'),
        new AutoScalingGroup(autoScalingGroupName: 'app-dev-v005'),
        new AutoScalingGroup(autoScalingGroupName: 'app-test-v010'),
        new AutoScalingGroup(autoScalingGroupName: 'app-dev-detail-v015'),
        new AutoScalingGroup(autoScalingGroupName: 'app-dev-detail2-v020'),
        new AutoScalingGroup(autoScalingGroupName: 'app-dev-detail3-v025')
      ]
    )

    where:
    stack   | freeFormDetails  || expected
    null    | null             || 'app-v001'
    'dev'   | null             || 'app-dev-v005'
    'test'  | null             || 'app-test-v010'
    'dev'   | 'detail'         || 'app-dev-detail-v015'
    'dev'   | 'detail2'        || 'app-dev-detail2-v020'
    'none'  | null             || null
    'dev'   | 'none'           || null
  }

}
