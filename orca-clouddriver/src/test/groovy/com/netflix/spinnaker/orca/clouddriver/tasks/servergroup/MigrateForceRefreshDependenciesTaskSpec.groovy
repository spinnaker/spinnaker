/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject

class MigrateForceRefreshDependenciesTaskSpec extends Specification {

  @Subject
  def task = new MigrateForceRefreshDependenciesTask()
  def stage = new Stage(Execution.newPipeline("orca"), "refreshTask")
  def taskId = new TaskId(UUID.randomUUID().toString())

  CloudDriverCacheService cacheService = Mock(CloudDriverCacheService)

  void setup() {
    task.cacheService = cacheService
    stage.context = [
      cloudProvider: 'aws',
      target       : [
        region     : 'us-east-1',
        credentials: 'test'
      ]
    ]
  }

  void 'should not refresh anything if there is nothing to refresh'() {
    given:
    stage.context["kato.tasks"] = [
      [
        resultObjects: [
          [someBogusResult: true],
          [serverGroupNames: ["new-asg-v002"],
           securityGroups  : []
          ]
        ]
      ]
    ]
    when:
    task.execute(stage)

    then:
    0 * _
  }

  void 'should refresh all security groups from the server group result itself'() {
    given:
    stage.context["kato.tasks"] = [
      [
        resultObjects: [
          [someBogusResult: true],
          [serverGroupNames: ["new-asg-v002"],
           securityGroups  : [
             [created: [[targetName: 'new-sg-1', credentials: 'test', vpcId: 'vpc-1']],
              reused : [[targetName: 'new-sg-2', credentials: 'prod', vpcId: 'vpc-2']]]
           ]
          ]
        ]
      ]
    ]
    when:
    task.execute(stage)

    then:
    1 * cacheService.forceCacheUpdate('aws', 'SecurityGroup', [securityGroupName: 'new-sg-1', region: 'us-east-1', account: 'test', vpcId: 'vpc-1'])
    1 * cacheService.forceCacheUpdate('aws', 'SecurityGroup', [securityGroupName: 'new-sg-2', region: 'us-east-1', account: 'prod', vpcId: 'vpc-2'])
    0 * _
  }

  void 'should refresh all security groups from the load balancers, and refresh the load balancers'() {
    given:
    stage.context["kato.tasks"] = [
      [
        resultObjects: [
          [serverGroupNames: ["new-asg-v002"],
           securityGroups  : [
             [created: [[targetName: 'new-sg-1', credentials: 'test', vpcId: 'vpc-1']],
              reused : []]
           ],
           loadBalancers   : [
             [targetName: 'newElb-vpc1', securityGroups: [[created: [], reused: [[targetName: 'new-sg-2', credentials: 'prod', vpcId: 'vpc-2']]]]]
           ]
          ]
        ]
      ]
    ]
    when:
    task.execute(stage)

    then:
    1 * cacheService.forceCacheUpdate('aws', 'LoadBalancer', [loadBalancerName: 'newElb-vpc1', region: 'us-east-1', account: 'test'])
    1 * cacheService.forceCacheUpdate('aws', 'SecurityGroup', [securityGroupName: 'new-sg-1', region: 'us-east-1', account: 'test', vpcId: 'vpc-1'])
    1 * cacheService.forceCacheUpdate('aws', 'SecurityGroup', [securityGroupName: 'new-sg-2', region: 'us-east-1', account: 'prod', vpcId: 'vpc-2'])
    0 * _
  }
}
