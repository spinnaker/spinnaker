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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import spock.lang.Specification
import spock.lang.Unroll

class WaitForNewUpInstancesLaunchTaskSpec extends Specification {
  def oortService = Mock(OortService)
  def task = new WaitForNewUpInstancesLaunchTask(oortService: oortService, objectMapper: new ObjectMapper())

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

    def stage = new Stage(Execution.newOrchestration("orca"), 'test', context)

    def oortResponse = oortResponse([
      instances: currentInstances.collect { [instanceId: it, health: [ [type: 'Discovery', state: healthState] ] ] }
    ])

    when:
    def response = task.execute(stage)

    then:
    1 * oortService.getServerGroup(account, region, serverGroup) >> oortResponse
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

  Response oortResponse(Map response) {
    def bytes = new TypedByteArray('application/json', task.objectMapper.writeValueAsBytes(response))
    new Response('http://oortse.cx', 200, 'OK', [], bytes)
  }

}
