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

package com.netflix.spinnaker.orca.zombie

import java.time.temporal.ChronoUnit
import com.netflix.spinnaker.orca.ActiveExecutionTracker
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.ExecutionStatus.*
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionEngine.v2
import static java.time.Instant.now
import static rx.Observable.just

class ZombiePipelineCleanupAgentSpec extends Specification {

  def executionRepository = Mock(ExecutionRepository)
  def activeExecutionTracker = Stub(ActiveExecutionTracker)

  @Subject zombieSlayer = new ZombiePipelineCleanupAgent(
    activeExecutionTracker,
    executionRepository,
    Lock.NEVER_LOCKED,
    "localhost"
  )

  @Shared instanceId = "i-01fa8ad4bcc4cb225"

  @SuppressWarnings("ChangeToOperator")
  @Shared startTime = now().minus(1, ChronoUnit.HOURS).toEpochMilli()

  def "does not kill a pipeline that's complete"() {
    given:
    executionRepository.retrievePipelines() >> just(pipeline)

    and:
    activeExecutionTracker.isActiveInstance(instanceId) >> false

    when:
    zombieSlayer.slayZombies()

    then:
    0 * executionRepository.updateStatus(pipeline.id, _)

    where:
    pipeline = pipelineWithStatus(SUCCEEDED)
  }

  def "kills a pipeline running on a zombie instance"() {
    given:
    executionRepository.retrievePipelines() >> just(pipeline)

    and:
    activeExecutionTracker.isActiveInstance(instanceId) >> false

    when:
    zombieSlayer.slayZombies()

    then:
    1 * executionRepository.updateStatus(pipeline.id, CANCELED)

    where:
    pipeline = pipelineWithStatus(RUNNING)
  }

  def "does not kill a pipeline that is running on an active instance"() {
    given:
    executionRepository.retrievePipelines() >> just(pipeline)

    and:
    activeExecutionTracker.isActiveInstance(instanceId) >> true

    when:
    zombieSlayer.slayZombies()

    then:
    0 * executionRepository.updateStatus(pipeline.id, _)

    where:
    pipeline = pipelineWithStatus(RUNNING)
  }

  def "does not kill a pipeline if the force flag is not set"() {
    given:
    executionRepository.retrievePipelines() >> just(pipeline)

    and:
    activeExecutionTracker.isActiveInstance(instanceId) >> true

    when:
    zombieSlayer.slayIfZombie(pipeline, false)

    then:
    0 * executionRepository.updateStatus(pipeline.id, CANCELED)

    where:
    pipeline = pipelineWithStatus(RUNNING)
  }

  def "does kill a pipeline if the force flag is set"() {
    given:
    executionRepository.retrievePipelines() >> just(pipeline)

    and:
    activeExecutionTracker.isActiveInstance(instanceId) >> true

    when:
    zombieSlayer.slayIfZombie(pipeline, true)

    then:
    1 * executionRepository.updateStatus(pipeline.id, CANCELED)

    where:
    pipeline = pipelineWithStatus(RUNNING)
  }

  private Pipeline pipelineWithStatus(ExecutionStatus status) {
    Pipeline
      .builder()
      .withId(1)
      .withApplication("Spinnaker")
      .withName("Test")
      .withExecutingInstance(instanceId)
      .withStartTime(startTime)
      .withStatus(status)
      .withExecutionEngine(v2)
      .build()
  }
}
