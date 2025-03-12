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

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.ModelUtils
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification
import spock.lang.Unroll

class WaitForNewUpInstancesLaunchTaskSpec extends Specification {
  CloudDriverService cloudDriverService = Mock()
  def task = new WaitForNewUpInstancesLaunchTask(cloudDriverService: cloudDriverService)

  @Unroll
  def 'waits for new instances to be launched and healthy'() {

    def context = [
      account         : account,
      application     : application,
      stack           : stack,
      region          : region,
      cloudProvider   : cloudProvider,
      asgName         : serverGroup,
      knownInstanceIds: knownInstanceIds,
      instanceIds     : terminatedInstanceIds
    ]

    def stage = new StageExecutionImpl(PipelineExecutionImpl.newOrchestration("orca"), 'test', context)

    def oortResponse = ModelUtils.serverGroup([
      instances: currentInstances.collect { [instanceId: it, health: [ [type: 'Discovery', state: healthState] ] ] }
    ])

    when:
    def response = task.execute(stage)

    then:
    1 * cloudDriverService.getServerGroup(account, region, serverGroup) >> oortResponse
    response.status == expectedStatus


    where:

    terminatedInstanceIds | knownInstanceIds | currentInstances      | healthState || expectedStatus
    []                    | []               | []                    | null        || ExecutionStatus.SUCCEEDED
    ['i-1']               | ['i-1', 'i-2']   | ['i-2']               | 'Up'        || ExecutionStatus.RUNNING
    ['i-1']               | ['i-1', 'i-2']   | ['i-2', 'i-3']        | 'Down'      || ExecutionStatus.RUNNING
    ['i-1']               | ['i-1', 'i-2']   | ['i-2', 'i-3']        | 'Up'        || ExecutionStatus.SUCCEEDED
    ['i-1', 'i-2']        | ['i-1', 'i-2']   | ['i-3']               | 'Up'        || ExecutionStatus.RUNNING
    ['i-1', 'i-2']        | ['i-1', 'i-2']   | ['i-3', 'i-4']        | 'Up'        || ExecutionStatus.SUCCEEDED
    ['i-1', 'i-2']        | ['i-1', 'i-2']   | ['i-3', 'i-4']        | 'Down'      || ExecutionStatus.RUNNING
    ['i-1', 'i-2']        | ['i-1', 'i-2']   | ['i-1', 'i-2', 'i-3'] | 'Up'        || ExecutionStatus.RUNNING

    account = 'test'
    application = 'foo'
    stack = 'test'
    cluster = "$application-$stack".toString()
    serverGroup = "$cluster-v000".toString()
    region = 'us-east-1'
    cloudProvider = 'aws'

  }
}
