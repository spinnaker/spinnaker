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

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.services.AsgService
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver
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
  def awsServerGroupNameResolver = new AWSServerGroupNameResolver('us-east-1', asgService)
  def credential = TestCredential.named('foo')
  def regionScopedProvider = Stub(RegionScopedProviderFactory.RegionScopedProvider) {
    getLaunchConfigurationBuilder() >> lcBuilder
    getAsgService() >> asgService
    getAWSServerGroupNameResolver() >> awsServerGroupNameResolver
  }

  void "deploy workflow is create launch config, create asg"() {
    setup:
    def asgName = "myasg-v000"
    def launchConfigName = "launchConfig"
    def mockAutoScalingWorker = Spy(AutoScalingWorker)
    mockAutoScalingWorker.application = "myasg"
    mockAutoScalingWorker.credentials = credential
    mockAutoScalingWorker.regionScopedProvider = regionScopedProvider

    when:
    mockAutoScalingWorker.deploy()

    then:
    1 * asgService.getTakenSlots(_) >> null
    1 * lcBuilder.buildLaunchConfiguration('myasg', null, _) >> launchConfigName
    1 * mockAutoScalingWorker.createAutoScalingGroup(asgName, launchConfigName) >> {}
  }

  void "deploy derives name from ancestor asg and sets the ancestor asg name in the task result"() {
    setup:
    def autoScalingWorker = new AutoScalingWorker(
      regionScopedProvider : regionScopedProvider,
      credentials: credential,
      application : "myasg",
      region : "us-east-1"
    )

    when:
    String asgName = autoScalingWorker.deploy()

    then:
    1 * asgService.getTakenSlots('myasg') >> [buildTakenSlot('myasg-v012')]
    1 * lcBuilder.buildLaunchConfiguration('myasg', null, _) >> 'lcName'
    asgName == 'myasg-v013'
    awsServerGroupNameResolver.getTask().resultObjects[0].ancestorServerGroupNameByRegion.get("us-east-1") == "myasg-v012"
  }

  private buildTakenSlot(String serverGroupName, long createdTime = 0) {
    return new AbstractServerGroupNameResolver.TakenSlot(
      serverGroupName: serverGroupName,
      sequence: Names.parseName(serverGroupName).sequence,
      createdTime: new Date(createdTime)
    )
  }

  def setup() {
    Task task = new DefaultTask("foo")
    TaskRepository.threadLocalTask.set(task)
  }

}
