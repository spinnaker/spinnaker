/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipeline.persistence

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.JenkinsTrigger
import com.netflix.spinnaker.orca.pipeline.model.PipelineTrigger
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.ExecutionStatus.*
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.*

@Subject(ExecutionRepository)
@Unroll
abstract class ExecutionRepositoryTck<T extends ExecutionRepository> extends Specification {

  @Subject
  ExecutionRepository repository

  @Subject
  ExecutionRepository previousRepository

  void setup() {
    repository = createExecutionRepository()
    previousRepository = createExecutionRepositoryPrevious()
  }

  abstract T createExecutionRepository()

  abstract T createExecutionRepositoryPrevious()

  def "can retrieve pipelines by status"() {
    given:
    def runningExecution = pipeline {
      status = RUNNING
      pipelineConfigId = "pipeline-1"
    }
    def succeededExecution = pipeline {
      status = SUCCEEDED
      pipelineConfigId = "pipeline-1"
    }

    when:
    repository.store(runningExecution)
    repository.store(succeededExecution)
    def pipelines = repository.retrievePipelinesForPipelineConfigId(
      "pipeline-1", new ExecutionCriteria(limit: 5, statuses: ["RUNNING", "SUCCEEDED", "TERMINAL"])
    ).toList()

    then:
    pipelines*.id.sort() == [runningExecution.id, succeededExecution.id].sort()

    when:
    pipelines = repository.retrievePipelinesForPipelineConfigId(
      "pipeline-1", new ExecutionCriteria(limit: 5, statuses: ["RUNNING"])
    ).toList()

    then:
    pipelines*.id.sort() == [runningExecution.id].sort()

    when:
    pipelines = repository.retrievePipelinesForPipelineConfigId(
      "pipeline-1", new ExecutionCriteria(limit: 5, statuses: ["TERMINAL"])
    ).toList()

    then:
    pipelines.isEmpty()
  }

  def "can retrieve orchestrations by status"() {
    given:
    def runningExecution = orchestration {
      status = RUNNING
      buildTime = 0
      trigger = new DefaultTrigger("manual")
    }
    def succeededExecution = orchestration {
      status = SUCCEEDED
      buildTime = 0
      trigger = new DefaultTrigger("manual")
    }

    when:
    repository.store(runningExecution)
    repository.store(succeededExecution)
    def orchestrations = repository.retrieveOrchestrationsForApplication(
      runningExecution.application, new ExecutionCriteria(limit: 5, statuses: ["RUNNING", "SUCCEEDED", "TERMINAL"])
    ).toList()

    then:
    orchestrations*.id.sort() == [runningExecution.id, succeededExecution.id].sort()

    when:
    orchestrations = repository.retrieveOrchestrationsForApplication(
      runningExecution.application, new ExecutionCriteria(limit: 5, statuses: ["RUNNING"])
    ).toList()

    then:
    orchestrations*.id.sort() == [runningExecution.id].sort()

    when:
    orchestrations = repository.retrieveOrchestrationsForApplication(
      runningExecution.application, new ExecutionCriteria(limit: 5, statuses: ["TERMINAL"])
    ).toList()

    then:
    orchestrations.isEmpty()
  }

  def "a pipeline can be retrieved after being stored"() {
    given:
    def pipeline = pipeline {
      application = "orca"
      name = "dummy-pipeline"
      trigger = new JenkinsTrigger("master", "job", 1, null)
      stage {
        type = "one"
        context = [foo: "foo"]
        refId = "1"
      }
      stage {
        type = "two"
        context = [bar: "bar"]
        refId = "2"
      }
      stage {
        type = "three"
        context = [baz: "baz"]
        refId = "3"
      }
    }
    def application = pipeline.application
    repository.store(pipeline)

    expect:
    repository.retrieve(PIPELINE).first().id == pipeline.id

    with(repository.retrieve(pipeline.type, pipeline.id)) {
      id == pipeline.id
      application == pipeline.application
      name == pipeline.name
      trigger == pipeline.trigger
      stages.type == pipeline.stages.type
      stages.execution.every {
        it.id == pipeline.id
      }
      stages.every {
        it.context == pipeline.namedStage(it.type).context
      }
    }
  }

  def "trying to retrieve an invalid #type.simpleName id throws an exception"() {
    when:
    repository.retrieve(type, "invalid")

    then:
    thrown ExecutionNotFoundException

    where:
    type << ExecutionType.values()
  }

  def "trying to delete a non-existent #type.simpleName id does not throw an exception"() {
    when:
    repository.delete(type, "invalid")

    then:
    notThrown ExecutionNotFoundException

    where:
    type << ExecutionType.values()
  }

  def "deleting a pipeline removes pipeline and stages"() {
    given:
    def pipeline = pipeline {
      stage { type = "one" }
      stage { type = "two" }
      stage {
        type = "one-a"
        name = "one-1"
      }
      stage {
        type = "one-b"
        name = "one-1"
      }
      stage {
        type = "one-a-a"
        name = "three"
      }
    }

    and:
    repository.store(pipeline)
    repository.delete(PIPELINE, pipeline.id)

    when:
    repository.retrieve(PIPELINE, pipeline.id)

    then:
    thrown ExecutionNotFoundException

    and:
    repository.retrieve(PIPELINE).toList() == []
  }

  def "updateStatus sets startTime to current time if new status is RUNNING"() {
    given:
    repository.store(execution)

    expect:
    with(repository.retrieve(execution.type, execution.id)) {
      startTime == null
    }

    when:
    repository.updateStatus(execution.type, execution.id, RUNNING)

    then:
    with(repository.retrieve(execution.type, execution.id)) {
      status == RUNNING
      startTime != null
    }

    where:
    execution << [pipeline {
      trigger = new PipelineTrigger(pipeline())
    }, orchestration {
      trigger = new DefaultTrigger("manual")
    }]
  }

  def "updateStatus sets endTime to current time if new status is #status"() {
    given:
    execution.startTime = System.currentTimeMillis()
    repository.store(execution)

    expect:
    with(repository.retrieve(execution.type, execution.id)) {
      startTime != null
      endTime == null
    }

    when:
    repository.updateStatus(execution.type, execution.id, status)

    then:
    with(repository.retrieve(execution.type, execution.id)) {
      status == status
      endTime != null
    }

    where:
    execution                                                | status
    pipeline { trigger = new PipelineTrigger(pipeline()) }   | CANCELED
    orchestration { trigger = new DefaultTrigger("manual") } | SUCCEEDED
    orchestration { trigger = new DefaultTrigger("manual") } | TERMINAL
  }

  def "updateStatus does not set endTime if a pipeline never started"() {
    given:
    repository.store(execution)

    expect:
    with(repository.retrieve(execution.type, execution.id)) {
      startTime == null
      endTime == null
    }

    when:
    repository.updateStatus(execution.type, execution.id, status)

    then:
    with(repository.retrieve(execution.type, execution.id)) {
      status == status
      endTime == null
    }

    where:
    execution                                              | status
    pipeline { trigger = new PipelineTrigger(pipeline()) } | CANCELED
  }

  def "cancelling a not-yet-started execution updates the status immediately"() {
    given:
    def execution = pipeline()
    repository.store(execution)

    expect:
    with(repository.retrieve(execution.type, execution.id)) {
      status == NOT_STARTED
    }

    when:
    repository.cancel(execution.type, execution.id)


    then:
    with(repository.retrieve(execution.type, execution.id)) {
      canceled
      status == CANCELED
    }
  }

  def "cancelling a running execution does not update the status immediately"() {
    given:
    def execution = pipeline()
    repository.store(execution)
    repository.updateStatus(execution.type, execution.id, RUNNING)

    expect:
    with(repository.retrieve(execution.type, execution.id)) {
      status == RUNNING
    }

    when:
    repository.cancel(execution.type, execution.id)


    then:
    with(repository.retrieve(execution.type, execution.id)) {
      canceled
      status == RUNNING
    }
  }

  @Unroll
  def "cancelling a running execution with a user adds a 'canceledBy' field, and an optional 'cancellationReason' field"() {
    given:
    def execution = pipeline()
    def user = "user@netflix.com"
    repository.store(execution)
    repository.updateStatus(execution.type, execution.id, RUNNING)

    expect:
    with(repository.retrieve(execution.type, execution.id)) {
      status == RUNNING
    }

    when:
    repository.cancel(execution.type, execution.id, user, reason)


    then:
    with(repository.retrieve(execution.type, execution.id)) {
      canceled
      canceledBy == user
      status == RUNNING
      cancellationReason == expectedCancellationReason
    }

    where:
    reason             || expectedCancellationReason
    "some good reason" || "some good reason"
    ""                 || null
    null               || null
  }

  def "pausing/resuming a running execution will set appropriate 'paused' details"() {
    given:
    def execution = pipeline()
    repository.store(execution)
    repository.updateStatus(execution.type, execution.id, RUNNING)

    when:
    repository.pause(execution.type, execution.id, "user@netflix.com")

    then:
    with(repository.retrieve(execution.type, execution.id)) {
      status == PAUSED
      paused.pauseTime != null
      paused.resumeTime == null
      paused.pausedBy == "user@netflix.com"
      paused.pausedMs == 0
      paused.paused == true
    }

    when:
    repository.resume(execution.type, execution.id, "another@netflix.com")

    then:
    with(repository.retrieve(execution.type, execution.id)) {
      status == RUNNING
      paused.pauseTime != null
      paused.resumeTime != null
      paused.pausedBy == "user@netflix.com"
      paused.resumedBy == "another@netflix.com"
      paused.pausedMs == (paused.resumeTime - paused.pauseTime)
      paused.paused == false
    }
  }

  @Unroll
  def "should only #method a #expectedStatus execution"() {
    given:
    def execution = pipeline()
    repository.store(execution)
    repository.updateStatus(execution.type, execution.id, status)

    when:
    repository."${method}"(execution.type, execution.id, "user@netflix.com")

    then:
    def e = thrown(IllegalStateException)
    e.message.startsWith("Unable to ${method} pipeline that is not ${expectedStatus}")

    where:
    method   | status      || expectedStatus
    "pause"  | PAUSED      || RUNNING
    "pause"  | NOT_STARTED || RUNNING
    "resume" | RUNNING     || PAUSED
    "resume" | NOT_STARTED || PAUSED
  }

  @Unroll
  def "should force resume an execution regardless of status"() {
    given:
    def execution = pipeline()
    repository.store(execution)
    repository.updateStatus(execution.type, execution.id, RUNNING)

    when:
    repository.pause(execution.type, execution.id, "user@netflix.com")
    execution = repository.retrieve(execution.type, execution.id)

    then:
    execution.paused.isPaused()

    when:
    repository.updateStatus(execution.type, execution.id, status)
    repository.resume(execution.type, execution.id, "user@netflix.com", true)
    execution = repository.retrieve(execution.type, execution.id)

    then:
    execution.status == RUNNING
    !execution.paused.isPaused()

    where:
    status << ExecutionStatus.values()
  }

  def "should return task ref for currently running orchestration by correlation id"() {
    given:
    def execution = orchestration {
      trigger = new DefaultTrigger("manual", "covfefe")
    }
    repository.store(execution)
    repository.updateStatus(execution.type, execution.id, RUNNING)

    when:
    def result = repository.retrieveOrchestrationForCorrelationId('covfefe')

    then:
    result.id == execution.id

    when:
    repository.updateStatus(execution.type, execution.id, SUCCEEDED)
    repository.retrieveOrchestrationForCorrelationId('covfefe')

    then:
    thrown(ExecutionNotFoundException)
  }

  def "parses the parent execution of a pipeline trigger"() {
    given:
    def execution = pipeline {
      trigger = new PipelineTrigger(pipeline())
    }
    repository.store(execution)

    expect:
    with(repository.retrieve(PIPELINE, execution.id)) {
      trigger.parentExecution instanceof Execution
    }
  }
}

