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

package com.netflix.spinnaker.orca.restart

import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.repository.JobRestartException
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.support.StaticApplicationContext
import rx.Observable
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.ExecutionStatus.*

class PipelineRecoveryListenerSpec extends Specification {

  def executionRepository = Stub(ExecutionRepository)
  def executionRunner = Mock(ExecutionRunner)
  @Shared currentInstanceId = "localhost"
  def registry = new DefaultRegistry()

  @Subject listener = new PipelineRecoveryListener(executionRepository, executionRunner, currentInstanceId, registry)
  def event = new ContextRefreshedEvent(new StaticApplicationContext())

  def "resumes pipelines that were in-progress on the current instance"() {
    given:
    executionRepository.retrievePipelines() >> Observable.just(pipeline1, pipeline2)

    when:
    listener.onApplicationEvent(event)

    then:
    1 * executionRunner.restart(pipeline1)
    1 * executionRunner.restart(pipeline2)

    where:
    pipeline1 = pipelineWithStatus(RUNNING, currentInstanceId)
    pipeline2 = pipelineWithStatus(NOT_STARTED, currentInstanceId)
  }

  def "continues if a restart fails"() {
    given:
    executionRepository.retrievePipelines() >> Observable.just(pipeline1, pipeline2)

    and:
    executionRunner.restart(pipeline1) >> { throw new JobRestartException("o noes") }

    when:
    listener.onApplicationEvent(event)

    then:
    1 * executionRunner.restart(pipeline2)

    where:
    pipeline1 = pipelineWithStatus(RUNNING, currentInstanceId)
    pipeline2 = pipelineWithStatus(NOT_STARTED, currentInstanceId)
  }

  def "tracks successful restarts"() {
    given:
    executionRepository.retrievePipelines() >> Observable.just(pipeline)

    when:
    listener.onApplicationEvent(event)

    then:
    successCount() == old(successCount()) + 1

    where:
    pipeline = pipelineWithStatus(RUNNING, currentInstanceId)
  }

  def "tracks failed restarts"() {
    given:
    executionRepository.retrievePipelines() >> Observable.just(pipeline)

    and:
    executionRunner.restart(pipeline) >> { throw new JobRestartException("o noes") }

    when:
    listener.onApplicationEvent(event)

    then:
    failureCount() == old(failureCount()) + 1

    where:
    pipeline = pipelineWithStatus(RUNNING, currentInstanceId)
  }

  def "ignores pipelines belonging to other instances"() {
    given:
    executionRepository.retrievePipelines() >> Observable.just(pipeline1, pipeline2)

    when:
    listener.onApplicationEvent(event)

    then:
    0 * executionRunner.restart(pipeline1)
    1 * executionRunner.restart(pipeline2)

    where:
    pipeline1 = pipelineWithStatus(RUNNING, "some other instance")
    pipeline2 = pipelineWithStatus(RUNNING, currentInstanceId)
  }

  @Unroll
  def "ignores pipelines in #status state"() {
    given:
    executionRepository.retrievePipelines() >> Observable.just(pipeline1, pipeline2)

    expect:
    pipeline1.status == status

    when:
    listener.onApplicationEvent(event)

    then:
    0 * executionRunner.restart(pipeline1)
    1 * executionRunner.restart(pipeline2)

    where:
    status    | _
    CANCELED  | _
    SUCCEEDED | _
    TERMINAL  | _
    SUSPENDED | _

    pipeline1 = pipelineWithStatus(status, currentInstanceId)
    pipeline2 = pipelineWithStatus(RUNNING, currentInstanceId)
  }

  private Pipeline pipelineWithStatus(ExecutionStatus status, String executingInstance) {
    def pipeline = new Pipeline(id: UUID.randomUUID(), executingInstance: executingInstance, status: status)
    if (status == CANCELED) {
      pipeline.canceled = true
    }
    return pipeline
  }

  private long successCount() {
    registry.counter("pipeline.restarts").count()
  }

  private long failureCount() {
    registry.counter("pipeline.failed.restarts").count()
  }
}
