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
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryPipelineStack
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class PipelineStarterQueueSpec extends Specification {

  @Subject
  PipelineStartTracker queue = new PipelineStartTracker(pipelineStack: new InMemoryPipelineStack())

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
    queue.addToStarted('123', '444')

    expect:
    queue.queueIfNotStarted(providedId, '333') == queued

    where:
    providedId || queued
    '123'      || true
    "not-123"  || false
  }

}
