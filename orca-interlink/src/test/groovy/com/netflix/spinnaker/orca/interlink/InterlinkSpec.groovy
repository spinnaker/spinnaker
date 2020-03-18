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
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.interlink.events.*
import com.netflix.spinnaker.orca.pipeline.CompoundExecutionOperator
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.time.Instant

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.ORCHESTRATION
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE

class InterlinkSpec extends Specification {
  ObjectMapper objectMapper = new ObjectMapper()
  @Shared def cancel = new CancelInterlinkEvent(ORCHESTRATION, "execId", "user", "reason")
  @Shared def pause = new PauseInterlinkEvent(PIPELINE, "execId", "user")
  @Shared def resume = new ResumeInterlinkEvent(PIPELINE, "execId", "user", false).withPartition("partition")
  @Shared def delete = new DeleteInterlinkEvent(ORCHESTRATION, "execId").withPartition("partition")
  @Shared def patch = new PatchStageInterlinkEvent(ORCHESTRATION, "execId", "stageId",
      '{"id": "someId", "context": {"value": 42}}').withPartition("partition")

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
        delete,
        patch
    ]
  }

  @Unroll
  def "event type #event.getEventType() applies the `#methodName` method"() {
    given:
    def executionOperator = Mock(CompoundExecutionOperator)

    when:
    event.withObjectMapper(new ObjectMapper()).applyTo(executionOperator)

    then:
    1 * executionOperator."$methodName"(*_)

    where:
    event  | methodName
    cancel | "cancel"
    pause  | "pause"
    resume | "resume"
    delete | "delete"
    patch  | "updateStage"
  }

  @Unroll
  def "applying a patch stage event patches the stage"() {
    given:
    def stage = new StageExecutionImpl(
        id: 'stageId',
        context: [untouched: 'untouched indeed', overridden: 'override me'],
        lastModified: stageLastModified)
    def execution = Mock(PipelineExecution)
    def repository = Mock(ExecutionRepository)
    def executionOperator = new CompoundExecutionOperator(
        repository,
        Mock(ExecutionRunner),
        new RetrySupport())
    def mapper = new ObjectMapper()

    and:
    def event = new PatchStageInterlinkEvent(ORCHESTRATION, "execId", "stageId",
      mapper.writeValueAsString(
          new StageExecutionImpl(id: 'stageId', context: [overridden: true, new_value: 42], lastModified: eventLastModified)
      )).withObjectMapper(mapper)

    when:
    event.applyTo(executionOperator)

    then:
    _ * repository.retrieve(event.executionType, event.executionId) >> execution
    1 * execution.stageById('stageId') >> stage
    1 * repository.storeStage(stage)

    // we apply values from the event's context map on top of the stage's context map
    stage.context.untouched == 'untouched indeed'
    stage.context.overridden == true
    stage.context.new_value == 42

    // and we propagate auth information when appropriate
    stage.lastModified == expectedLastModified

    where:
    stageLastModified          | eventLastModified                       || expectedLastModified
    null                       | null                                    || null
    lastModified('user')       | null                                    || stageLastModified
    lastModified('new')        | lastModified('old', 5)                  || eventLastModified // should warn about timestamp but still override
    null                       | lastModified('user')                    || eventLastModified
    lastModified('old', 5)     | lastModified('new', 2)                  || eventLastModified
    lastModified('old', 5, []) | lastModified('new', 2, ['someaccount']) || eventLastModified
  }

  private static StageExecution.LastModifiedDetails lastModified(String user, int minutesAgo = 0, Collection<String> allowedAccounts = []) {
    return new StageExecution.LastModifiedDetails(
        user: user,
        allowedAccounts: allowedAccounts,
        lastModifiedTime: Instant.now().minus(Duration.ofMinutes(minutesAgo)).toEpochMilli()
    )
  }
}
