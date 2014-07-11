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


package com.netflix.spinnaker.kato.deploy.aws

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeSubnetsResult
import com.amazonaws.services.ec2.model.Subnet
import com.amazonaws.services.ec2.model.Tag
import com.netflix.spinnaker.kato.config.BlockDevice
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.services.SecurityGroupService
import spock.lang.Specification

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
      1 * getSecurityGroupForApplication("myasg") >> "sg-1234"
    }

    when:
    mockAutoScalingWorker.deploy()

    then:
    1 * mockAutoScalingWorker.getAncestorAsg() >> null
    1 * mockAutoScalingWorker.getAutoScalingGroupName(0) >> asgName
    1 * mockAutoScalingWorker.getUserData(asgName, launchConfigName) >> { "" }
    1 * mockAutoScalingWorker.getLaunchConfigurationName(0) >> launchConfigName
    1 * mockAutoScalingWorker.createLaunchConfiguration(_, _, _) >> { launchConfigName }
    1 * mockAutoScalingWorker.createAutoScalingGroup(_, _) >> {}
  }

  void "deploy favors security groups of ancestor asg"() {
    setup:
    def mockAutoScalingWorker = Spy(AutoScalingWorker)
    mockAutoScalingWorker.application = "myasg"
    mockAutoScalingWorker.securityGroupService = Mock(SecurityGroupService) {
      1 * getSecurityGroupForApplication("myasg") >> "sg-1234"
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
      1 * getSecurityGroupForApplication("myasg")
      1 * createSecurityGroup("myasg", null) >> "sg-1234"
    }

    when:
    mockAutoScalingWorker.deploy()

    then:
    1 * mockAutoScalingWorker.getUserData(_, _) >> null
    1 * mockAutoScalingWorker.getLaunchConfigurationName(_) >> "launchConfigName"

    1 * mockAutoScalingWorker.getAncestorAsg() >> null
    1 * mockAutoScalingWorker.createLaunchConfiguration("launchConfigName", null, _) >> { "launchConfigName" }
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
    def worker = new AutoScalingWorker(amazonEC2: ec2, subnetType: AutoScalingWorker.SubnetType.INTERNAL,
      availabilityZones: ["us-west-1a"])

    when:
    def results = worker.subnetIds

    then:
    results.first() == "123"
  }

  void "block device mappings are applied when available"() {
    setup:
    def autoscaling = Mock(AmazonAutoScaling)
    def worker = new AutoScalingWorker(autoScaling: autoscaling, blockDevices: [new BlockDevice(deviceName: "/dev/sdb", size: 125)])

    when:
    worker.createLaunchConfiguration(null, null, null)

    then:
    1 * autoscaling.createLaunchConfiguration(_) >> { CreateLaunchConfigurationRequest request ->
      assert request.blockDeviceMappings
      assert request.blockDeviceMappings.first().deviceName == "/dev/sdb"
      assert request.blockDeviceMappings.first().ebs.volumeSize == 125
    }
  }

}
