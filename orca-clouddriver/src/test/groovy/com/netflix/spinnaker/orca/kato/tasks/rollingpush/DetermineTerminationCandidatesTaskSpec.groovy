/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.tasks.rollingpush

import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.ModelUtils
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification
import spock.lang.Unroll

class DetermineTerminationCandidatesTaskSpec extends Specification {

  CloudDriverService cloudDriverService = Mock()
  def task = new DetermineTerminationCandidatesTask(cloudDriverService: cloudDriverService)

  @Unroll
  def 'should order and filter instances correctly'() {
    def termination = buildTermination(order, relaunchAllInstances, totalRelaunches, concurrentRelaunches, instances)

    def context = [
      account       : account,
      application   : application,
      stack         : stack,
      region        : region,
      cloudProvider : cloudProvider,
      asgName       : serverGroup,
    ]
    if (termination) {
      context.termination = termination
    }

    def stage = new StageExecutionImpl(PipelineExecutionImpl.newOrchestration("orca"), 'test', context)

    def sg = ModelUtils.serverGroup([
      instances: knownInstanceIds.inject([]) { List l, id -> l << [instanceId: id, launchTime: l.size()] }
    ])

    when:
    def response = task.execute(stage)

    then:
    1 * cloudDriverService.getServerGroupFromCluster(application, account, cluster, serverGroup, region, cloudProvider) >> sg
    response.context.terminationInstanceIds == expectedTerminations
    response.context.knownInstanceIds.toSet() == knownInstanceIds.toSet()
    expectedTerminations == response.context.terminationInstanceIds

    where:
    order    | relaunchAllInstances | totalRelaunches | instances                    || expectedTerminations
    null     | null                 | null            | null                         || ['i-1', 'i-2', 'i-3', 'i-4']
    'oldest' | null                 | null            | null                         || ['i-1', 'i-2', 'i-3', 'i-4']
    'newest' | null                 | null            | null                         || ['i-4', 'i-3', 'i-2', 'i-1']
    'oldest' | null                 | 2               | null                         || ['i-1', 'i-2']
    'oldest' | true                 | 2               | null                         || ['i-1', 'i-2', 'i-3', 'i-4']
    'oldest' | false                | 2               | null                         || ['i-1', 'i-2']
    'newest' | false                | 2               | null                         || ['i-4', 'i-3']
    null     | true                 | 4               | null                         || ['i-1', 'i-2', 'i-3', 'i-4']
    null     | true                 | 4               | ['i-4', 'i-9']               || ['i-4']
    null     | true                 | 4               | ['i-4', 'i-2', 'i-3']        || ['i-4', 'i-2', 'i-3']
    'given'  | null                 | null            | ['i-3', 'i-2', 'i-4', 'i-1'] || ['i-3', 'i-2', 'i-4', 'i-1']

    account = 'test'
    application = 'foo'
    stack = 'test'
    cluster = "$application-$stack".toString()
    serverGroup = "$cluster-v000".toString()
    region = 'us-east-1'
    cloudProvider = 'aws'
    knownInstanceIds = ['i-1', 'i-2', 'i-3', 'i-4']
    concurrentRelaunches = totalRelaunches
  }

  Map buildTermination(String order = null, Boolean relaunchAllInstances = null, Integer totalRelaunches = null, Integer concurrentRelaunches = null, ArrayList instances = null) {
    def termination = [:]
    if (order) {
      termination.order = order
    }
    if (relaunchAllInstances != null) {
      termination.relaunchAllInstances = relaunchAllInstances
    }
    if (totalRelaunches != null) {
      termination.totalRelaunches = totalRelaunches
    }
    if (concurrentRelaunches != null) {
      termination.concurrentRelaunches = concurrentRelaunches
    }
    if (instances != null) {
      termination.instances = instances
    }

    return termination
  }
}
