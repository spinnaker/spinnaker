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

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.netflix.spinnaker.kato.aws.services.AsgService
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import spock.lang.Specification

class AutoScalingWorkerUnitSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "deploy workflow is create launch config, create asg"() {
    setup:
    def asgName = "myasg-v000"
    def launchConfigName = "launchConfig"
    def lcBuilder = Mock(LaunchConfigurationBuilder)
    def mockAutoScalingWorker = Spy(AutoScalingWorker)
    mockAutoScalingWorker.application = "myasg"
    mockAutoScalingWorker.launchConfigurationBuilder = lcBuilder
    mockAutoScalingWorker.asgService = Mock(AsgService) {
      1 * getAncestorAsg(_, _, _) >> null
    }

    when:
    mockAutoScalingWorker.deploy()

    then:
    1 * mockAutoScalingWorker.getAutoScalingGroupName(0) >> asgName
    1 * lcBuilder.buildLaunchConfiguration('myasg', null, _) >> launchConfigName
    1 * mockAutoScalingWorker.createAutoScalingGroup(asgName, launchConfigName) >> {}
  }

  void "deploy derives name from ancestor asg"() {
    setup:
    def lcBuilder = Mock(LaunchConfigurationBuilder)
    def mockAutoScalingWorker = Spy(AutoScalingWorker)
    mockAutoScalingWorker.launchConfigurationBuilder = lcBuilder
    mockAutoScalingWorker.application = "myasg"
    mockAutoScalingWorker.region = "us-east-1"
    mockAutoScalingWorker.asgService = Mock(AsgService) {
      1 * getAncestorAsg('myasg', _, _) >> new AutoScalingGroup().withAutoScalingGroupName('myasg-v012')
    }

    when:
    mockAutoScalingWorker.deploy()

    then:
    1 * lcBuilder.buildLaunchConfiguration('myasg', null, _) >> 'lcName'
    1 * mockAutoScalingWorker.createAutoScalingGroup('myasg-v013', 'lcName') >> {}
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

}
