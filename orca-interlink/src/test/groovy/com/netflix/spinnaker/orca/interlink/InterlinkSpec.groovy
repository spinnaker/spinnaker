/*
 * Copyright 2020 Netflix, Inc.
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
 */

package com.netflix.spinnaker.orca.interlink

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.interlink.events.*
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE

class InterlinkSpec extends Specification {
  ObjectMapper objectMapper = new ObjectMapper()
  @Shared def cancel = new CancelInterlinkEvent(ORCHESTRATION, "execId", "user", "reason")
  @Shared def pause = new PauseInterlinkEvent(PIPELINE, "execId", "user")
  @Shared def resume = new ResumeInterlinkEvent(PIPELINE, "execId", "user", false).withPartition("partition")
  @Shared def delete = new DeleteInterlinkEvent(ORCHESTRATION, "execId").withPartition("partition")

  @Unroll
  def "can parse execution event type #event.getEventType()"() {
    when:
    def payload = objectMapper.writeValueAsString(event)

    then:
    def mappedEvent = objectMapper.readValue(payload, InterlinkEvent.class)
    mappedEvent == event

    where:
    event << [
        cancel,
        pause,
        resume,
        delete
    ]
  }

  @Unroll
  def "event type #event.getEventType() applies the `#methodName` method"() {
    given:
    def executionRepo = Mock(ExecutionRepository)

    when:
    event.applyTo(executionRepo)

    then:
    1 * executionRepo."$methodName"(*_)

    where:
    event  | methodName
    cancel | "cancel"
    pause  | "pause"
    resume | "resume"
    delete | "delete"
  }
}
