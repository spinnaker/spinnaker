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
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.kato.data.task.DefaultTask
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

class AutoScalingWorkerUnitSpec extends Specification {

  /*
  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }
  */

  @Autowired
  TaskRepository taskRepository

  def lcBuilder = Mock(LaunchConfigurationBuilder)
  def asgService = Mock(AsgService)
  def regionScopedProvider = Stub(RegionScopedProviderFactory.RegionScopedProvider) {
    getLaunchConfigurationBuilder() >> lcBuilder
    getAsgService() >> asgService
  }

  def setup() {
    Task task = new DefaultTask("foo")
    TaskRepository.threadLocalTask.set(task)
  }

  void "deploy workflow is create launch config, create asg"() {
    setup:
    def asgName = "myasg-v000"
    def launchConfigName = "launchConfig"
    def mockAutoScalingWorker = Spy(AutoScalingWorker)
    mockAutoScalingWorker.application = "myasg"
    mockAutoScalingWorker.regionScopedProvider = regionScopedProvider

    when:
    mockAutoScalingWorker.deploy()

    then:
    1 * asgService.getAncestorAsg(_) >> null
    1 * lcBuilder.buildLaunchConfiguration('myasg', null, _) >> launchConfigName
    1 * mockAutoScalingWorker.createAutoScalingGroup(asgName, launchConfigName) >> {}
  }

  void "deploy derives name from ancestor asg"() {
    setup:
    def mockAutoScalingWorker = Spy(AutoScalingWorker)
    mockAutoScalingWorker.regionScopedProvider = regionScopedProvider
    mockAutoScalingWorker.application = "myasg"
    mockAutoScalingWorker.region = "us-east-1"

    when:
    mockAutoScalingWorker.deploy()

    then:
    1 * asgService.getAncestorAsg('myasg') >> new AutoScalingGroup().withAutoScalingGroupName('myasg-v012')
    1 * lcBuilder.buildLaunchConfiguration('myasg', null, _) >> 'lcName'
    1 * mockAutoScalingWorker.createAutoScalingGroup('myasg-v013', 'lcName') >> {}
    mockAutoScalingWorker.getTask().resultObjects[0].ancestorServerGroupNames == "us-east-1:myasg-v012"
    mockAutoScalingWorker.getTask().resultObjects[1].ancestorServerGroupNameByRegion.get("us-east-1") == "myasg-v012"
  }

}
