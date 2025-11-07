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

package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.services.internal.OrcaService
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Unroll

class PipelineServiceSpec extends Specification {

  OrcaServiceSelector orcaServiceSelector = Mock(OrcaServiceSelector)
  OrcaService orcaService = Mock(OrcaService)

  void 'startPipeline should add notifications to existing notifications'() {
    given:
    def service = new PipelineService(
      applicationService: Mock(ApplicationService) {
        1 * getPipelineConfigForApplication('app', 'p-id') >> [
          notifications: [[type: 'email']]
        ]
      },
      orcaServiceSelector: orcaServiceSelector
    )
    when:
    service.trigger('app', 'p-id', [notifications: [[type: 'sms']]])

    then:
    1 * orcaServiceSelector.select() >> { orcaService }
    1 * orcaService.startPipeline({ p -> p.notifications.type == ['email', 'sms'] }, _) >> Calls.response(null)
  }

  @Unroll
  void 'startPipeline should set notifications to those on trigger'() {
    given:
    def service = new PipelineService(
      applicationService: Mock(ApplicationService) {
        1 * getPipelineConfigForApplication('app', 'p-id') >> [
          notifications: config
        ]
      },
      orcaServiceSelector: orcaServiceSelector
    )
    when:
    service.trigger('app', 'p-id', [notifications: trigger])

    then:
    1 * orcaServiceSelector.select() >> { orcaService }
    1 * orcaService.startPipeline({ p -> p.notifications == expected }, _) >> Calls.response(null)

    where:
    config                     | trigger                    || expected
    null                       | null                       || null
    null                       | []                         || null
    []                         | null                       || []
    []                         | []                         || []
    null                       | [[type: 'a']]              || [[type: 'a']]
    null                       | [[type: 'a'], [type: 'b']] || [[type: 'a'], [type: 'b']]
    null                       | [[type: 'a'], [type: 'b']] || [[type: 'a'], [type: 'b']]
    [[type: 'a']]              | null                       || [[type: 'a']]
    [[type: 'a'], [type: 'b']] | null                       || [[type: 'a'], [type: 'b']]
    [[type: 'a'], [type: 'b']] | [[type: 'c']]              || [[type: 'a'], [type: 'b'], [type: 'c']]
  }

  @Unroll
  void 'startPipeline should throw exceptions if required parameters are not supplied'() {
    given:
    def service = new PipelineService(
      applicationService: Mock(ApplicationService) {
        1 * getPipelineConfigForApplication('app', 'p-id') >> [
          parameterConfig: [[name: 'param1', required: true]]
        ]
      },
      orcaServiceSelector: orcaServiceSelector
    )
    when:
    orcaServiceSelector.select() >> { orcaService }
    (0..1) * orcaService.startPipeline(_ as Map,  _) >> Calls.response(null)
    def didThrow = false
    try {
      service.trigger('app', 'p-id', trigger)
    } catch (IllegalArgumentException ignored) {
      didThrow = true
    }

    then:
    didThrow == isThrown

    where:
    trigger                       || isThrown
    [:]                           || true
    [parameters: null]            || true
    [parameters: [:]]             || true
    [parameters: [param1: null]]  || true
    [parameters: [param1: false]] || false
    [parameters: [param1: 0]]     || false
    [parameters: [param1: 'a']]   || false
  }

  @Unroll
  void "getPipeline passes requireUpToDateVersion to orca (#requireUpToDateVersion)"() {
    given:
    def executionId = "some-execution-id"
    def service = new PipelineService(
      applicationService: Mock(ApplicationService),
      orcaServiceSelector: orcaServiceSelector
    )

    when:
    service.getPipeline(executionId, requireUpToDateVersion)

    then:
    1 * orcaServiceSelector.select() >> { orcaService }
    1 * orcaService.getPipeline(executionId, requireUpToDateVersion) >> Calls.response([:])
    0 * orcaServiceSelector._
    0 * orcaService._

    where:
    requireUpToDateVersion << [false, true]
  }

  @Unroll
  void "getPipelineStatus passes readReplicaRequirement to orca (#readReplicaRequirement)"() {
    given:
    def executionId = "some-execution-id"
    def executionStatus = "arbitrary status"

    ResponseBody responseBody = ResponseBody.create(MediaType.parse("text/plain"), executionStatus)
    Response<ResponseBody> response = Response.success(responseBody)
    def service = new PipelineService(
      applicationService: Mock(ApplicationService),
      orcaServiceSelector: orcaServiceSelector
    )

    when:
    def actualStatus = service.getPipelineStatus(executionId, readReplicaRequirement)

    then:
    actualStatus == executionStatus

    1 * orcaServiceSelector.select() >> { orcaService }
    1 * orcaService.getPipelineStatus(executionId, readReplicaRequirement) >> Calls.response(response)
    0 * orcaServiceSelector._
    0 * orcaService._

    where:
    readReplicaRequirement << ["NONE", "PRESENT", "up_to_date", "bogus"]
  }
}
