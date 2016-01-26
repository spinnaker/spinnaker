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

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.services.AsgService
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.model.Cluster
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

class AutoScalingWorkerUnitSpec extends Specification {

  @Autowired
  TaskRepository taskRepository

  def lcBuilder = Mock(LaunchConfigurationBuilder)
  def asgService = Mock(AsgService)
  def clusterProvider = Mock(ClusterProvider)
  def awsServerGroupNameResolver = new AWSServerGroupNameResolver('test', 'us-east-1', asgService, [clusterProvider])
  def credential = TestCredential.named('foo')
  def regionScopedProvider = Stub(RegionScopedProviderFactory.RegionScopedProvider) {
    getLaunchConfigurationBuilder() >> lcBuilder
    getAsgService() >> asgService
    getAWSServerGroupNameResolver() >> awsServerGroupNameResolver
  }

  def setup() {
    Task task = new DefaultTask("task")
    TaskRepository.threadLocalTask.set(task)
  }

  void "deploy workflow is create launch config, create asg"() {
    setup:
    def asgName = "myasg-stack-details-v000"
    def launchConfigName = "launchConfig"
    def mockAutoScalingWorker = Spy(AutoScalingWorker)
    mockAutoScalingWorker.application = "myasg"
    mockAutoScalingWorker.stack = "stack"
    mockAutoScalingWorker.freeFormDetails = "details"
    mockAutoScalingWorker.credentials = credential
    mockAutoScalingWorker.regionScopedProvider = regionScopedProvider

    when:
    mockAutoScalingWorker.deploy()

    then:
    1 * lcBuilder.buildLaunchConfiguration('myasg', null, _) >> launchConfigName
    1 * mockAutoScalingWorker.createAutoScalingGroup(asgName, launchConfigName) >> {}
    1 * clusterProvider.getCluster('myasg', 'test', 'myasg-stack-details') >> { null }
    0 * clusterProvider._
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
    1 * lcBuilder.buildLaunchConfiguration('myasg', null, _) >> 'lcName'
    1 * clusterProvider.getCluster('myasg', 'test', 'myasg') >> {
      new Cluster.SimpleCluster(type: 'aws', serverGroups: [
        sG('myasg-v011', 0, 'us-east-1'), sG('myasg-v099', 1, 'us-west-1')
      ])
    }
    1 * asgService.getAutoScalingGroup('myasg-v012') >> { new AutoScalingGroup() }
    1 * asgService.getAutoScalingGroup('myasg-v013') >> { null }
    0 * _

    asgName == 'myasg-v013'
    awsServerGroupNameResolver.getTask().resultObjects[0].ancestorServerGroupNameByRegion.get("us-east-1") == "myasg-v011"
  }

  static ServerGroup sG(String name, Long createdTime, String region) {
    return new ServerGroup.SimpleServerGroup(name: name, createdTime: createdTime, region: region)
  }
}
