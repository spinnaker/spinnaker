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
import spock.lang.Specification
import spock.lang.Unroll

class PipelineServiceSpec extends Specification {

  void 'startPipeline should add notifications to existing notifications'() {
    given:
    OrcaServiceSelector orcaServiceSelector = Mock(OrcaServiceSelector)
    OrcaService orcaService = Mock(OrcaService)
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
    1 * orcaService.startPipeline({ p -> p.notifications.type == ['email', 'sms'] }, _)
  }

  @Unroll
  void 'startPipeline should set notifications to those on trigger'() {
    given:
    OrcaServiceSelector orcaServiceSelector = Mock(OrcaServiceSelector)
    OrcaService orcaService = Mock(OrcaService)
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
    1 * orcaService.startPipeline({ p -> p.notifications == expected }, _)

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
    OrcaServiceSelector orcaServiceSelector = Mock(OrcaServiceSelector)
    OrcaService orcaService = Mock(OrcaService)
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
}
