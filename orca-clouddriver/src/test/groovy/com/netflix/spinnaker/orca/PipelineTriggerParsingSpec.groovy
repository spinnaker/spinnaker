/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca

import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Execution
import spock.lang.Specification

class PipelineTriggerParsingSpec extends Specification {

  def mapper = OrcaObjectMapper.newInstance()

  def "can parse"() {
    given:
    def execution = mapper.readValue(json, Execution)

    when:
    def parent = mapper.convertValue(execution.trigger.parentExecution, Execution)

    then:
    parent.id == "84099610-f292-4cab-bd5a-49ecf8570ffe"

    when:
    def grandpa = parent.trigger.parentExecution

    then:
    grandpa.id == "eecce10e-2a99-41e4-b6aa-db2aa73c63db"
  }

  def json = getClass().getResource("/pipelinetrigger.json")
}
