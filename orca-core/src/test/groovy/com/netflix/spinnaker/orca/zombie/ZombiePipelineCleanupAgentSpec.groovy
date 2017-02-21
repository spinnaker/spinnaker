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

import java.time.Clock
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.context.ApplicationContext
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.ExecutionStatus.*
import static com.netflix.spinnaker.orca.zombie.ZombiePipelineCleanupAgent.TOLERANCE
import static java.time.Instant.now
import static java.util.concurrent.TimeUnit.HOURS
import static java.util.concurrent.TimeUnit.SECONDS
import static rx.Observable.just

class ZombiePipelineCleanupAgentSpec extends Specification {

  def executionRepository = Mock(ExecutionRepository)
  def clock = Clock.fixed(now(), ZoneId.systemDefault())

  def task = new RandomTask()
  def applicationContext = Stub(ApplicationContext) {
    getBean(RandomTask) >> task
  }

  @Subject zombieSlayer = new ZombiePipelineCleanupAgent(
    executionRepository,
    applicationContext,
    Lock.NEVER_LOCKED,
    "localhost",
    clock
    ,
  )

  @SuppressWarnings("ChangeToOperator")
  @Shared long startTime = now().minus(1, ChronoUnit.HOURS).toEpochMilli()

  def "does not kill a pipeline that's complete"() {
    given:
    pipeline.stages.first().tasks << new DefaultTask(
      implementingClass: RandomTask,
      startTime: overdue(),
      status: SUCCEEDED
    )
    executionRepository.retrievePipelines() >> just(pipeline)

    when:
    zombieSlayer.slayZombies()

    then:
    0 * executionRepository.updateStatus(pipeline.id, _)

    where:
    pipeline = Pipeline
      .builder()
      .withId(1)
      .withExecutionEngine("v2")
      .withStage("whatever")
      .withStatus(SUCCEEDED)
      .withStartTime(startTime)
      .build()
  }

  def "kills a pipeline with a zombie task"() {
    given:
    pipeline.stages.first().tasks << new DefaultTask(
      implementingClass: RandomTask,
      startTime: overdue(),
      status: RUNNING
    )
    executionRepository.retrievePipelines() >> just(pipeline)

    when:
    zombieSlayer.slayZombies()

    then:
    1 * executionRepository.updateStatus(pipeline.id, CANCELED)

    where:
    pipeline = Pipeline
      .builder()
      .withId(1)
      .withExecutionEngine("v2")
      .withStage("whatever")
      .withStatus(RUNNING)
    .withStartTime(startTime)
      .build()
  }

  def "does not kill a pipeline that isn't overdue"() {
    given:
    pipeline.stages.first().tasks << new DefaultTask(
      implementingClass: RandomTask,
      startTime: notOverdue(),
      status: RUNNING
    )
    executionRepository.retrievePipelines() >> just(pipeline)

    when:
    zombieSlayer.slayZombies()

    then:
    0 * executionRepository.updateStatus(pipeline.id, _)

    where:
    pipeline = Pipeline
      .builder()
      .withId(1)
      .withExecutionEngine("v2")
      .withStage("whatever")
      .withStatus(RUNNING)
      .withStartTime(startTime)
      .build()
  }

  def "does not kill a pipeline that is paused"() {
    given:
    pipeline.stages.first().tasks << new DefaultTask(
      implementingClass: RandomTask,
      startTime: overdue(),
      status: PAUSED
    )
    executionRepository.retrievePipelines() >> just(pipeline)

    and:
    pipeline.paused = new Execution.PausedDetails(pauseTime: overdue())

    when:
    zombieSlayer.slayZombies()

    then:
    0 * executionRepository.updateStatus(pipeline.id, _)

    where:
    pipeline = Pipeline
      .builder()
      .withId(1)
      .withExecutionEngine("v2")
      .withStage("whatever")
      .withStatus(PAUSED)
      .withStartTime(startTime)
      .build()
  }

  @SuppressWarnings("ChangeToOperator")
  def "does not kill a pipeline that is overdue but was previously paused for a while"() {
    given:
    pipeline.stages.first().tasks << new DefaultTask(
      implementingClass: RandomTask,
      startTime: overdue(),
      status: RUNNING
    )
    executionRepository.retrievePipelines() >> just(pipeline)

    and:
    pipeline.paused = new Execution.PausedDetails(pauseTime: overdue(), resumeTime: clock.instant().minus(2, ChronoUnit.MINUTES).toEpochMilli())

    when:
    zombieSlayer.slayZombies()

    then:
    0 * executionRepository.updateStatus(pipeline.id, _)

    where:
    pipeline = Pipeline
      .builder()
      .withId(1)
      .withExecutionEngine("v2")
      .withStage("whatever")
      .withStatus(RUNNING)
      .withStartTime(startTime)
      .build()
  }

  def "does not kill a pipeline if the force flag is not set"() {
    given:
    pipeline.stages.first().tasks << new DefaultTask(
      implementingClass: RandomTask,
      startTime: notOverdue(),
      status: RUNNING
    )
    executionRepository.retrievePipelines() >> just(pipeline)

    when:
    zombieSlayer.slayIfZombie(pipeline, false)

    then:
    0 * executionRepository.updateStatus(pipeline.id, CANCELED)

    where:
    pipeline = Pipeline
      .builder()
      .withId(1)
      .withExecutionEngine("v2")
      .withStage("whatever")
      .withStatus(RUNNING)
      .withStartTime(startTime)
      .build()
  }

  def "does kill a pipeline if the force flag is set"() {
    given:
    pipeline.stages.first().tasks << new DefaultTask(
      implementingClass: RandomTask,
      startTime: notOverdue(),
      status: RUNNING
    )
    executionRepository.retrievePipelines() >> just(pipeline)

    when:
    zombieSlayer.slayIfZombie(pipeline, true)

    then:
    1 * executionRepository.updateStatus(pipeline.id, CANCELED)

    where:
    pipeline = Pipeline
      .builder()
      .withId(1)
      .withExecutionEngine("v2")
      .withStage("whatever")
      .withStatus(RUNNING)
      .withStartTime(startTime)
      .build()
  }

  @SuppressWarnings("ChangeToOperator")
  private long overdue() {
    clock
      .instant()
      .minusMillis(task.timeout)
      .minus(TOLERANCE)
      .minusSeconds(1)
      .toEpochMilli()
  }

  @SuppressWarnings("ChangeToOperator")
  private long notOverdue() {
    clock
      .instant()
      .minusMillis(task.timeout)
      .minus(TOLERANCE)
      .plusSeconds(1)
      .toEpochMilli()
  }

  static class RandomTask implements RetryableTask {
    @Override
    TaskResult execute(Stage stage) {
      new DefaultTaskResult(RUNNING)
    }

    final long backoffPeriod = SECONDS.toMillis(10)
    final long timeout = HOURS.toMillis(1)
  }

}
