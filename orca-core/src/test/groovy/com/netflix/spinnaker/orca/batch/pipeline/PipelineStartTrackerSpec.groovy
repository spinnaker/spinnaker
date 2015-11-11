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

package com.netflix.spinnaker.orca.batch.pipeline

import com.netflix.spinnaker.orca.pipeline.PipelineStartTracker
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryPipelineStack
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class PipelineStartTrackerSpec extends Specification {
  def executionRepository = Mock(ExecutionRepository)

  @Subject
  PipelineStartTracker queue = new PipelineStartTracker(
    pipelineStack: new InMemoryPipelineStack(),
    executionRepository: executionRepository
  )

  void "should return a list of started jobs"() {
    given:
    queue.addToStarted("123", "123-started")
    queue.addToStarted("234", "234-started")

    expect:
    queue.allStartedExecutions == ['234-started', '123-started']
  }

  void "should mark a job as done"() {
    given:
    queue.addToStarted("123", "123-started")

    expect:
    queue.allStartedExecutions.size() == 1

    when:
    queue.markAsFinished("123", "123-started")

    then:
    queue.allStartedExecutions.empty
  }

  void "should get a list of queued jobs"() {
    given:
    executionRepository.retrievePipelinesForPipelineConfigId(_, _) >> {
      return rx.Observable.from([
          buildPipeline("xxx")
      ])
    }

    queue.addToStarted('123', 'xxx')
    (1..5).each {
      queue.queueIfNotStarted("123", "123-queue-${it}")
    }

    expect:
    queue.getQueuedPipelines('123').size() == 5
    queue.getQueuedPipelines('123').first() == "123-queue-5"

    when:
    queue.removeFromQueue('123', '123-queue-5')
    queue.removeFromQueue('123', '123-queue-4')

    then:
    queue.getQueuedPipelines('123').size() == 3
    queue.getQueuedPipelines('123').first() == "123-queue-3"
  }

  @Unroll
  void "should return correct queueIfNotStarted values"() {
    given:
    executionRepository.retrievePipelinesForPipelineConfigId(_, _) >> {
      return rx.Observable.from([
        buildPipeline("444")
      ])
    }
    queue.addToStarted('123', '444')

    expect:
    queue.queueIfNotStarted(providedId, '333') == queued

    where:
    providedId || queued
    '123'      || true
    "not-123"  || false
  }

  void "should remove STARTED executions if they are no longer RUNNING"() {
    given:
    def pipelineConfigId = "pipeline-config"
    executionRepository.retrievePipelinesForPipelineConfigId(_, _) >> {
      return rx.Observable.from([
        buildPipeline("1")
      ])
    }

    queue.addToStarted(pipelineConfigId, "1")
    queue.addToStarted(pipelineConfigId, "2")

    expect:
    queue.getStartedPipelines(pipelineConfigId) == ["2", "1"]

    when:
    queue.queueIfNotStarted(pipelineConfigId, "3")

    then:
    queue.getStartedPipelines(pipelineConfigId) == ["1"]
  }

  private static Pipeline buildPipeline(String id) {
    def pipeline = new Pipeline()
    pipeline.id = id
    return pipeline
  }
}
