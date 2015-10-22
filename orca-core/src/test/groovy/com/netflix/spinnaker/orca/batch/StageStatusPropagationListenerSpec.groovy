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

package com.netflix.spinnaker.orca.batch

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.JedisExecutionRepository
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParameter
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.StepExecution
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.util.Pool
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class StageStatusPropagationListenerSpec extends Specification {

  @Shared @AutoCleanup("destroy") EmbeddedRedis embeddedRedis

  def setupSpec() {
    embeddedRedis = EmbeddedRedis.embed()
  }

  def cleanup() {
    embeddedRedis.jedis.withCloseable { it.flushDB() }
  }

  Pool<Jedis> jedisPool = embeddedRedis.pool

  def mapper = new OrcaObjectMapper()
  def executionRepository = new JedisExecutionRepository(new NoopRegistry(), jedisPool, 1, 50)
  @Subject listener = new StageStatusPropagationListener(executionRepository)
  @Shared random = Random.newInstance()

  def "updates the stage status when a task execution completes"() {
    given: "a pipeline model"
    def pipeline = Pipeline.builder().withStage(stageType).build()
    pipeline.stages[0].tasks << new DefaultTask(id: "task1")
    pipeline.stages[0].tasks << new DefaultTask(id: "task2")
    executionRepository.store(pipeline)

    and: "a batch execution context"
    def jobExecution = new JobExecution(id, new JobParameters(pipeline: new JobParameter(pipeline.id)))
    def stepExecution = new StepExecution("${pipeline.stages[0].id}.${stageType}.task1", jobExecution)

    and: "the first task runs"
    executeTaskReturning ExecutionStatus.SUCCEEDED, stepExecution
    pipeline.stages[0].tasks[0].status = ExecutionStatus.SUCCEEDED
    executionRepository.storeStage(pipeline.stages[0])

    when: "the listener is triggered"
    def exitStatus = listener.afterStep stepExecution
    def currentStage = executionRepository.retrievePipeline(pipeline.id).stages.first()

    then: "it updates the status of the stage to RUNNING because it is not the final task"
    currentStage.status == ExecutionStatus.RUNNING
    currentStage.endTime == null

    and: "the exit status of the batch step is unchanged"
    exitStatus == null

    when: "the last task runs"
    stepExecution = new StepExecution("${pipeline.stages[0].id}.${stageType}.task2", jobExecution)
    executeTaskReturning ExecutionStatus.SUCCEEDED, stepExecution
    pipeline.stages[0].tasks[-1].status = ExecutionStatus.SUCCEEDED
    executionRepository.storeStage(pipeline.stages[0])

    and: "the listener is triggered"
    exitStatus = listener.afterStep stepExecution
    currentStage = executionRepository.retrievePipeline(pipeline.id).stages.first()

    then: "it updates the status of the stage to SUCCEEDED because it is the final task"
    currentStage.status == ExecutionStatus.SUCCEEDED
    currentStage.endTime != null

    and: "the exit status of the batch step is unchanged"
    exitStatus == null

    where:
    id = random.nextLong()
    stageType = "foo"
  }

  def "does not update stage status, startTime or endTime if the stage was originally complete"() {
    given: "a pipeline model"
    def pipeline = Pipeline.builder().withStage(stageType).build()
    pipeline.stages[0].tasks << new DefaultTask(id: "task1")

    pipeline.stages[0].status = ExecutionStatus.SUCCEEDED
    pipeline.stages[0].startTime = 50L
    pipeline.stages[0].endTime = 100L

    executionRepository.store(pipeline)

    and: "a batch execution context"
    def jobExecution = new JobExecution(id, new JobParameters(pipeline: new JobParameter(pipeline.id)))
    def stepExecution = new StepExecution("${pipeline.stages[0].id}.${stageType}.task1", jobExecution)

    when: "the listener is triggered"
    listener.beforeStep stepExecution
    def currentStage = executionRepository.retrievePipeline(pipeline.id).stages.first()

    then:
    currentStage.status == ExecutionStatus.SUCCEEDED
    currentStage.startTime == 50L
    currentStage.endTime == 100L

    when: "the first task runs"
    executeTaskReturning ExecutionStatus.SUCCEEDED, stepExecution
    pipeline.stages[0].tasks[0].status = ExecutionStatus.SUCCEEDED
    executionRepository.storeStage(pipeline.stages[0])

    listener.afterStep stepExecution
    currentStage = executionRepository.retrievePipeline(pipeline.id).stages.first()

    then:
    currentStage.status == ExecutionStatus.SUCCEEDED
    currentStage.startTime == 50L
    currentStage.endTime == 100L

    where:
    id = random.nextLong()
    stageType = "foo"
  }

  /**
   * This just emulates a task running and the associated updates to the batch
   * execution context.
   *
   * @param taskStatus the status the task should return.
   * @param stepExecution the batch execution context we want to update.
   */
  private void executeTaskReturning(ExecutionStatus taskStatus, StepExecution stepExecution) {
    stepExecution.executionContext.put("orcaTaskStatus", taskStatus)
  }
}
