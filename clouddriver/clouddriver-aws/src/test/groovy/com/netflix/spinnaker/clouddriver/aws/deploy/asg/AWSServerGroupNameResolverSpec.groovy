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

package com.netflix.spinnaker.clouddriver.aws.deploy.asg

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.netflix.spinnaker.clouddriver.aws.services.AsgService
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.model.Cluster
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import spock.lang.Specification

class AWSServerGroupNameResolverSpec extends Specification {
  def asgService = Mock(AsgService)
  def amazonClusterProvider = Mock(ClusterProvider)
  def googleClusterProvider = Mock(ClusterProvider)

  def account = 'test'
  def region = 'us-west-1'

  void setup() {
    Task task = new DefaultTask("task")
    TaskRepository.threadLocalTask.set(task)
  }


  void "should build TakenSlots based on cached cluster data and Amazon data"() {
    given:
    def clusterProviders = [amazonClusterProvider, googleClusterProvider]
    def resolver = new AWSServerGroupNameResolver(account, region, asgService, clusterProviders)

    when:
    def takenSlots = resolver.getTakenSlots('application-stack-details')

    then:
    1 * amazonClusterProvider.getCluster('application', account, 'application-stack-details') >> {
      // should only include server groups in the target region
      new Cluster.SimpleCluster(type: 'aws', serverGroups: [
          sG('application-stack-details-v000', 1, region),
          sG('application-stack-details-v999', 0, region),
          sG('application-stack-details-v001', 0, 'us-east-1')
      ])
    }
    1 * googleClusterProvider.getCluster('application', account, 'application-stack-details') >> {
      // wrong cluster type, should not be included in taken slots
      new Cluster.SimpleCluster(type: 'google', serverGroups: [
        sG('application-stack-details-v001', 0, region)
      ])
    }
    1 * asgService.getAutoScalingGroup('application-stack-details-v000') >> new AutoScalingGroup()
    1 * asgService.getAutoScalingGroup('application-stack-details-v999') >> null
    0 * _

    takenSlots == [
        new AbstractServerGroupNameResolver.TakenSlot('application-stack-details-v000', 0, new Date(1))
    ]
  }

  void "should ensure that next server group does not already exist (and increment as necessary until new server group name is found)"() {
    given:
    def clusterProviders = [amazonClusterProvider]
    def resolver = new AWSServerGroupNameResolver(account, region, asgService, clusterProviders, 5)

    and:
    def clusterName = 'application-stack-details'

    when:
    def nextServerGroupName = resolver.resolveNextServerGroupName('application', 'stack', 'details', false)

    then:
    1 * amazonClusterProvider.getCluster('application', account, clusterName) >> {
      new Cluster.SimpleCluster(type: 'aws', serverGroups: [
        sG("${clusterName}-v999", 0, region)
      ])
    }
    1 * asgService.getAutoScalingGroup("${clusterName}-v999") >> new AutoScalingGroup()
    1 * asgService.getAutoScalingGroup("${clusterName}-v000") >> { null }
    0 * _

    nextServerGroupName == "${clusterName}-v000".toString()

    when:
    nextServerGroupName = resolver.resolveNextServerGroupName('application', 'stack', 'details', false)

    then:
    1 * amazonClusterProvider.getCluster('application', account, clusterName) >> {
      new Cluster.SimpleCluster(type: 'aws', serverGroups: [
        sG('application-stack-details-v999', 0, region)
      ])
    }
    (0..4).each {
      1 * asgService.getAutoScalingGroup(String.format("${clusterName}-v%03d", it)) >> { new AutoScalingGroup() }
    }
    1 * asgService.getAutoScalingGroup("${clusterName}-v999") >> new AutoScalingGroup()
    1 * asgService.getAutoScalingGroup("${clusterName}-v005") >> { null }
    0 * _

    nextServerGroupName == "${clusterName}-v005".toString()
  }

  void "should raise IllegalArgumentException if unable to determine next server group"() {
    given:
    def clusterProviders = [amazonClusterProvider]
    def resolver = new AWSServerGroupNameResolver(account, region, asgService, clusterProviders, 5)

    and:
    def clusterName = 'application-stack-details'

    when:
    resolver.resolveNextServerGroupName('application', 'stack', 'details', false)

    then:
    1 * amazonClusterProvider.getCluster('application', account, clusterName) >> {
      new Cluster.SimpleCluster(type: 'aws', serverGroups: [
        sG("${clusterName}-v999", 0, region)
      ])
    }
    (0..5).each {
      // represents an edge case where the cache is sufficiently out of date and there are 1000 existing server groups
      1 * asgService.getAutoScalingGroup(String.format("${clusterName}-v%03d", it)) >> { new AutoScalingGroup() }
    }

    thrown(IllegalArgumentException)
  }

  void "should raise IllegalArgumentException in cluster name is invalid"() {
    given:
    def clusterProviders = [amazonClusterProvider]
    def resolver = new AWSServerGroupNameResolver(account, region, asgService, clusterProviders, 1)

    when:
    resolver.resolveNextServerGroupName('application', 'stack', 'details with spaces', false)

    then:
    thrown(IllegalArgumentException)
  }

  static ServerGroup sG(String name, Long createdTime, String region) {
    return new SimpleServerGroup(name: name, createdTime: createdTime, region: region)
  }
}
