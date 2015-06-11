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
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.OrchestrationStage
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import spock.lang.Specification

class DetermineTerminationCandidatesTaskSpec extends Specification {

  def oortService = Mock(OortService)
  def task = new DetermineTerminationCandidatesTask(oortService: oortService, objectMapper: new ObjectMapper())

  def 'should order instances correctly'() {
    def termination = buildTermination(order, relaunchAllInstances, totalRelaunches)

    def context = [
      account     : account,
      application : application,
      stack       : stack,
      region      : region,
      providerType: providerType,
      asgName     : serverGroup,
    ]
    if (termination) {
      context.termination = termination
    }

    def stage = new OrchestrationStage(new Orchestration(), 'test', context)

    def oortResponse = oortResponse([
      instances: knownInstanceIds.inject([]) { List l, id -> l << [instanceId: id, launchTime: l.size()] }
    ])

    when:
    def response = task.execute(stage)

    then:
    1 * oortService.getServerGroup(application, account, cluster, serverGroup, region, providerType) >> oortResponse
    response.stageOutputs.terminationInstanceIds == expectedTerminations
    response.stageOutputs.knownInstanceIds.toSet() == knownInstanceIds.toSet()

    where:
    order    | relaunchAllInstances | totalRelaunches | expectedTerminations
    null     | null                 | null            | ['i-1', 'i-2', 'i-3', 'i-4']
    'oldest' | null                 | null            | ['i-1', 'i-2', 'i-3', 'i-4']
    'newest' | null                 | null            | ['i-4', 'i-3', 'i-2', 'i-1']
    'oldest' | null                 | 2               | ['i-1', 'i-2']
    'oldest' | true                 | 2               | ['i-1', 'i-2', 'i-3', 'i-4']
    'oldest' | false                | 2               | ['i-1', 'i-2']
    'newest' | false                | 2               | ['i-4', 'i-3']


    account = 'test'
    application = 'foo'
    stack = 'test'
    cluster = "$application-$stack".toString()
    serverGroup = "$cluster-v000".toString()
    region = 'us-east-1'
    providerType = 'aws'
    knownInstanceIds = ['i-1', 'i-2', 'i-3', 'i-4']

  }

  Map buildTermination(String order = null, Boolean relaunchAllInstances = null, Integer totalRelaunches = null, Integer concurrentRelaunches = null) {
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

    return termination
  }

  Response oortResponse(Map response) {
    def bytes = new TypedByteArray('application/json', task.objectMapper.writeValueAsBytes(response))
    new Response('http://oortse.cx', 200, 'OK', [], bytes)
  }
}
