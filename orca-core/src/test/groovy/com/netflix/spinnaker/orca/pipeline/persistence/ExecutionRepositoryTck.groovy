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

import java.util.concurrent.CountDownLatch
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.JedisExecutionRepository
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import rx.schedulers.Schedulers
import spock.lang.*
import static com.netflix.spinnaker.orca.ExecutionStatus.*
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.newStage
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import static java.util.concurrent.TimeUnit.SECONDS

@Subject(ExecutionRepository)
@Unroll
abstract class ExecutionRepositoryTck<T extends ExecutionRepository> extends Specification {

  @Subject
  T repository

  void setup() {
    repository = createExecutionRepository()
  }

  abstract T createExecutionRepository()

  def "if an execution does not have an id it is assigned one when stored"() {
    expect:
    execution.id == null

    when:
    repository.store(execution)

    then:
    execution.id != null

    where:
    execution << [new Pipeline(buildTime: 0), new Orchestration()]

  }

  def "if an execution already has an id it is not re-assigned when stored"() {
    given:
    repository.store(execution)

    when:
    repository.store(execution)

    then:
    execution.id == old(execution.id)

    where:
    execution << [new Pipeline(buildTime: 0), new Orchestration(id: "a-preassigned-id")]
  }

  def "can update an execution's context"() {
    given:
    repository.store(execution)

    when:
    repository.storeExecutionContext(execution.id, ["value": execution.class.simpleName])
    def storedExecution = (execution instanceof Pipeline) ? repository.retrievePipeline(execution.id) : repository.retrieveOrchestration(execution.id)

    then:
    storedExecution.id == execution.id
    storedExecution.context == ["value": storedExecution.class.simpleName]

    where:
    execution << [new Pipeline(buildTime: 0), new Orchestration(id: "a-preassigned-id")]
  }

  def "can retrieve pipelines by status"() {
    given:
    def runningExecution = new Pipeline(status: RUNNING, pipelineConfigId: "pipeline-1", buildTime: 0)
    def succeededExecution = new Pipeline(status: SUCCEEDED, pipelineConfigId: "pipeline-1", buildTime: 0)

    when:
    repository.store(runningExecution)
    repository.store(succeededExecution)
    def pipelines = repository.retrievePipelinesForPipelineConfigId(
      "pipeline-1", new ExecutionCriteria(limit: 5, statuses: ["RUNNING", "SUCCEEDED", "TERMINAL"])
    ).subscribeOn(Schedulers.io()).toList().toBlocking().single()

    then:
    pipelines*.id.sort() == [runningExecution.id, succeededExecution.id].sort()

    when:
    pipelines = repository.retrievePipelinesForPipelineConfigId(
      "pipeline-1", new ExecutionCriteria(limit: 5, statuses: ["RUNNING"])
    ).subscribeOn(Schedulers.io()).toList().toBlocking().single()

    then:
    pipelines*.id.sort() == [runningExecution.id].sort()

    when:
    pipelines = repository.retrievePipelinesForPipelineConfigId(
      "pipeline-1", new ExecutionCriteria(limit: 5, statuses: ["TERMINAL"])
    ).subscribeOn(Schedulers.io()).toList().toBlocking().single()

    then:
    pipelines.isEmpty()
  }

  def "can retrieve orchestrations by status"() {
    given:
    def runningExecution = new Orchestration(status: RUNNING, buildTime: 0, application: "application")
    def succeededExecution = new Orchestration(status: SUCCEEDED, buildTime: 0, application: "application")

    when:
    repository.store(runningExecution)
    repository.store(succeededExecution)
    def orchestrations = repository.retrieveOrchestrationsForApplication(
      "application", new ExecutionCriteria(limit: 5, statuses: ["RUNNING", "SUCCEEDED", "TERMINAL"])
    ).subscribeOn(Schedulers.io()).toList().toBlocking().single()

    then:
    orchestrations*.id.sort() == [runningExecution.id, succeededExecution.id].sort()

    when:
    orchestrations = repository.retrieveOrchestrationsForApplication(
      "application", new ExecutionCriteria(limit: 5, statuses: ["RUNNING"])
    ).subscribeOn(Schedulers.io()).toList().toBlocking().single()

    then:
    orchestrations*.id.sort() == [runningExecution.id].sort()

    when:
    orchestrations = repository.retrieveOrchestrationsForApplication(
      "application", new ExecutionCriteria(limit: 5, statuses: ["TERMINAL"])
    ).subscribeOn(Schedulers.io()).toList().toBlocking().single()

    then:
    orchestrations.isEmpty()
  }

  def "a pipeline can be retrieved after being stored"() {
    given:
    repository.store(pipeline)

    expect:
    repository.retrievePipelines().toBlocking().first().id == pipeline.id

    with(repository.retrievePipeline(pipeline.id)) {
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

    where:
    application = "orca"
    pipeline = Pipeline
      .builder()
      .withApplication(application)
      .withName("dummy-pipeline")
      .withTrigger(name: "some-jenkins-job", lastBuildLabel: 1)
      .withStage("one", "one", [foo: "foo"])
      .withStage("two", "two", [bar: "bar"])
      .withStage("three", "three", [baz: "baz"])
      .build()
  }

  def "trying to retrieve an invalid #type.simpleName id throws an exception"() {
    when:
    repository."retrieve${type.simpleName}"("invalid")

    then:
    thrown ExecutionNotFoundException

    where:
    type << [Pipeline, Orchestration]
  }

  def "trying to delete a non-existent #type.simpleName id does not throw an exception"() {
    when:
    repository."delete${type.simpleName}"("invalid")

    then:
    notThrown ExecutionNotFoundException

    where:
    type << [Pipeline, Orchestration]
  }

  def "deleting a pipeline removes pipeline and stages"() {
    given:
    def application = "someApp"
    def pipeline = Pipeline
      .builder()
      .withStage("one", "one", [:])
      .withStage("two", "two", [:])
      .withStage("one-a", "one-1", [:])
      .withStage("one-b", "one-1", [:])
      .withStage("one-a-a", "three", [:])
      .withApplication(application)
      .build()

    and:
    repository.store(pipeline)
    repository.deletePipeline(pipeline.id)

    when:
    repository.retrievePipeline(pipeline.id)

    then:
    thrown ExecutionNotFoundException

    and:
    repository.retrievePipelines().toList().toBlocking().first() == []
  }

  def "updateStatus sets startTime to current time if new status is RUNNING"() {
    given:
    repository.store(execution)

    expect:
    with(repository."retrieve$type"(execution.id)) {
      startTime == null
    }

    when:
    repository.updateStatus(execution.id, RUNNING)

    then:
    with(repository."retrieve$type"(execution.id)) {
      status == RUNNING
      startTime != null
    }

    where:
    execution << [new Pipeline(buildTime: 0), new Orchestration()]
    type = execution.getClass().simpleName
  }

  def "updateStatus sets endTime to current time if new status is #status"() {
    given:
    repository.store(execution)

    expect:
    with(repository."retrieve$type"(execution.id)) {
      endTime == null
    }

    when:
    repository.updateStatus(execution.id, status)

    then:
    with(repository."retrieve$type"(execution.id)) {
      status == status
      endTime != null
    }

    where:
    execution                  | status
    new Pipeline(buildTime: 0) | CANCELED
    new Orchestration()        | SUCCEEDED
    new Orchestration()        | TERMINAL

    type = execution.getClass().simpleName
  }

  def "cancelling a not-yet-started execution updates the status immediately"() {
    given:
    def execution = new Pipeline(buildTime: 0)
    repository.store(execution)

    expect:
    with(repository.retrievePipeline(execution.id)) {
      status == NOT_STARTED
    }

    when:
    repository.cancel(execution.id)


    then:
    with(repository.retrievePipeline(execution.id)) {
      canceled
      status == CANCELED
    }
  }

  def "cancelling a running execution does not update the status immediately"() {
    given:
    def execution = new Pipeline(buildTime: 0)
    repository.store(execution)
    repository.updateStatus(execution.id, RUNNING)

    expect:
    with(repository.retrievePipeline(execution.id)) {
      status == RUNNING
    }

    when:
    repository.cancel(execution.id)


    then:
    with(repository.retrievePipeline(execution.id)) {
      canceled
      status == RUNNING
    }
  }

  @Unroll
  def "cancelling a running execution with a user adds a 'canceledBy' field, and an optional 'cancellationReason' field"() {
    given:
    def execution = new Pipeline(buildTime: 0)
    def user = "user@netflix.com"
    repository.store(execution)
    repository.updateStatus(execution.id, RUNNING)

    expect:
    with(repository.retrievePipeline(execution.id)) {
      status == RUNNING
    }

    when:
    repository.cancel(execution.id, user, reason)


    then:
    with(repository.retrievePipeline(execution.id)) {
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
    def execution = new Pipeline(buildTime: 0)
    repository.store(execution)
    repository.updateStatus(execution.id, RUNNING)

    when:
    repository.pause(execution.id, "user@netflix.com")

    then:
    with(repository.retrievePipeline(execution.id)) {
      status == PAUSED
      paused.pauseTime != null
      paused.resumeTime == null
      paused.pausedBy == "user@netflix.com"
      paused.pausedMs == 0
      paused.paused == true
    }

    when:
    repository.resume(execution.id, "another@netflix.com")

    then:
    with(repository.retrievePipeline(execution.id)) {
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
    def execution = new Pipeline(buildTime: 0)
    repository.store(execution)
    repository.updateStatus(execution.id, status)

    when:
    repository."${method}"(execution.id, "user@netflix.com")

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
    def execution = new Pipeline(buildTime: 0)
    repository.store(execution)
    repository.updateStatus(execution.id, RUNNING)

    when:
    repository.pause(execution.id, "user@netflix.com")
    execution = repository.retrievePipeline(execution.id)

    then:
    execution.paused.isPaused()

    when:
    repository.updateStatus(execution.id, status)
    repository.resume(execution.id, "user@netflix.com", true)
    execution = repository.retrievePipeline(execution.id)

    then:
    execution.status == RUNNING
    !execution.paused.isPaused()

    where:
    status << ExecutionStatus.values()
  }
}

class JedisExecutionRepositorySpec extends ExecutionRepositoryTck<JedisExecutionRepository> {

  @Shared
  @AutoCleanup("destroy")
  EmbeddedRedis embeddedRedis

  @Shared
  @AutoCleanup("destroy")
  EmbeddedRedis embeddedRedisPrevious

  def setupSpec() {
    embeddedRedis = EmbeddedRedis.embed()
    embeddedRedisPrevious = EmbeddedRedis.embed()
  }

  def cleanup() {
    embeddedRedis.jedis.withCloseable { it.flushDB() }
    embeddedRedisPrevious.jedis.withCloseable { it.flushDB() }
  }

  Pool<Jedis> jedisPool = embeddedRedis.pool
  Pool<Jedis> jedisPoolPrevious = embeddedRedisPrevious.pool

  @AutoCleanup
  def jedis = jedisPool.resource

  @Override
  JedisExecutionRepository createExecutionRepository() {
    new JedisExecutionRepository(new NoopRegistry(), jedisPool, Optional.of(jedisPoolPrevious), 1, 50)
  }

  def "cleans up indexes of non-existent executions"() {
    given:
    jedis.sadd("allJobs:pipeline", id)

    when:
    def result = repository.retrievePipelines().toList().toBlocking().first()

    then:
    result.isEmpty()

    and:
    !jedis.sismember("allJobs:pipeline", id)

    where:
    id = "some-pipeline-id"
  }

  def "storing/deleting a pipeline updates the executionsByPipeline set"() {
    given:
    def pipeline = Pipeline
      .builder()
      .withStage("one", "one", [:])
      .withApplication("someApp")
      .build()

    when:
    repository.store(pipeline)

    then:
    jedis.zrange(JedisExecutionRepository.executionsByPipelineKey(pipeline.pipelineConfigId), 0, 1) == [
      pipeline.id
    ] as Set<String>

    when:
    repository.deletePipeline(pipeline.id)
    repository.retrievePipeline(pipeline.id)

    then:
    thrown ExecutionNotFoundException

    and:
    repository.retrievePipelines().toList().toBlocking().first() == []
    jedis.zrange(JedisExecutionRepository.executionsByPipelineKey(pipeline.pipelineConfigId), 0, 1).isEmpty()
  }

  @Unroll
  def "retrieving orchestrations limits the number of returned results"() {
    given:
    repository.store(new Orchestration(application: "orca", buildTime: 0))
    repository.store(new Orchestration(application: "orca", buildTime: 0))
    repository.store(new Orchestration(application: "orca", buildTime: 0))
    repository.store(new Orchestration(application: "orca", buildTime: 0))

    when:
    def retrieved = repository.retrieveOrchestrationsForApplication("orca", new ExecutionCriteria(limit: limit))
      .toList().toBlocking().first()

    then:
    retrieved.size() == actual

    where:
    limit || actual
    0     || 0
    1     || 1
    2     || 2
    4     || 4
    100   || 4
  }

  def "can retrieve orchestrations from multiple redis stores"() {
    given:
    repository.store(new Orchestration(application: "orca", executingInstance: "current"))
    repository.store(new Orchestration(application: "orca", executingInstance: "current"))
    repository.store(new Orchestration(application: "orca", executingInstance: "current"))

    and:
    def previousRepository = new JedisExecutionRepository(new NoopRegistry(), jedisPoolPrevious, Optional.empty(), 1, 50)
    previousRepository.store(new Orchestration(application: "orca", executingInstance: "previous"))
    previousRepository.store(new Orchestration(application: "orca", executingInstance: "previous"))
    previousRepository.store(new Orchestration(application: "orca", executingInstance: "previous"))

    when:
    // TODO-AJ limits are current applied to each backing redis
    def retrieved = repository.retrieveOrchestrationsForApplication("orca", new ExecutionCriteria(limit: 2))
      .toList().toBlocking().first()

    then:
    // orchestrations are stored in an unsorted set and results are non-deterministic
    retrieved.findAll { it.executingInstance == "current" }.size() == 2
    retrieved.findAll { it.executingInstance == "previous" }.size() == 2
  }

  def "can delete orchestrations from multiple redis stores"() {
    given:
    def orchestration1 = new Orchestration(application: "orca")
    repository.store(orchestration1)

    and:
    def previousRepository = new JedisExecutionRepository(new NoopRegistry(), jedisPoolPrevious, Optional.empty(), 1, 50)
    def orchestration2 = new Orchestration(application: "orca")
    previousRepository.store(orchestration2)

    when:
    repository.deleteOrchestration(orchestration1.id)
    def retrieved = repository.retrieveOrchestrationsForApplication("orca", new ExecutionCriteria(limit: 2))
      .toList().toBlocking().first()

    then:
    retrieved*.id == [orchestration2.id]

    when:
    repository.deleteOrchestration(orchestration2.id)
    retrieved = repository.retrieveOrchestrationsForApplication("orca", new ExecutionCriteria(limit: 2))
      .toList().toBlocking().first()

    then:
    retrieved.isEmpty()
  }

  def "can retrieve pipelines from multiple redis stores"() {
    given:
    repository.store(new Pipeline(application: "orca", pipelineConfigId: "pipeline-1", buildTime: 10))
    repository.store(new Pipeline(application: "orca", pipelineConfigId: "pipeline-1", buildTime: 11))
    repository.store(new Pipeline(application: "orca", pipelineConfigId: "pipeline-1", buildTime: 12))

    and:
    def previousRepository = new JedisExecutionRepository(new NoopRegistry(), jedisPoolPrevious, Optional.empty(), 1, 50)
    previousRepository.store(new Pipeline(application: "orca", pipelineConfigId: "pipeline-1", buildTime: 7))
    previousRepository.store(new Pipeline(application: "orca", pipelineConfigId: "pipeline-1", buildTime: 8))
    previousRepository.store(new Pipeline(application: "orca", pipelineConfigId: "pipeline-1", buildTime: 9))

    when:
    // TODO-AJ limits are current applied to each backing redis
    def retrieved = repository.retrievePipelinesForPipelineConfigId("pipeline-1", new ExecutionCriteria(limit: 2))
      .toList().toBlocking().first()

    then:
    // pipelines are stored in a sorted sets and results should be reverse buildTime ordered
    retrieved*.buildTime == [12L, 11L, 9L, 8L]
  }

  def "can delete pipelines from multiple redis stores"() {
    given:
    def pipeline1 = new Pipeline(application: "orca", pipelineConfigId: "pipeline-1", buildTime: 11)
    repository.store(pipeline1)

    and:
    def previousRepository = new JedisExecutionRepository(new NoopRegistry(), jedisPoolPrevious, Optional.empty(), 1, 50)
    def pipeline2 = new Pipeline(application: "orca", pipelineConfigId: "pipeline-1", buildTime: 10)
    previousRepository.store(pipeline2)

    when:
    repository.deletePipeline(pipeline1.id)
    def retrieved = repository.retrievePipelinesForPipelineConfigId("pipeline-1", new ExecutionCriteria(limit: 2))
      .toList().toBlocking().first()

    then:
    retrieved*.id == [pipeline2.id]

    when:
    repository.deletePipeline(pipeline2.id)
    retrieved = repository.retrievePipelinesForPipelineConfigId("pipeline-1", new ExecutionCriteria(limit: 2))
      .toList().toBlocking().first()

    then:
    retrieved.isEmpty()
  }

  def "should remove null 'stage' keys"() {
    given:
    def pipeline = Pipeline
      .builder()
      .withApplication("orca")
      .withName("dummy-pipeline")
      .withStage("one", "one", [foo: "foo"])
      .build()

    pipeline.stages[0].startTime = 100
    pipeline.stages[0].endTime = 200

    repository.store(pipeline)

    when:
    def fetchedPipeline = repository.retrievePipeline(pipeline.id)

    then:
    fetchedPipeline.stages[0].startTime == 100
    fetchedPipeline.stages[0].endTime == 200

    when:
    fetchedPipeline.stages[0].startTime = null
    fetchedPipeline.stages[0].endTime = null
    repository.storeStage(fetchedPipeline.stages[0])

    fetchedPipeline = repository.retrievePipeline(pipeline.id)

    then:
    fetchedPipeline.stages[0].startTime == null
    fetchedPipeline.stages[0].endTime == null
  }

  def "can remove a stage leaving other stages unaffected"() {
    given:
    def pipeline = Pipeline
      .builder()
      .withApplication("orca")
      .withName("dummy-pipeline")
      .withStage("one")
      .withStage("two")
      .withStage("three")
      .build()

    repository.store(pipeline)

    expect:
    repository.retrievePipeline(pipeline.id).stages.size() == 3

    when:
    repository.removeStage(pipeline, pipeline.namedStage("two").id)

    then:
    with(repository.retrievePipeline(pipeline.id)) {
      stages.size() == 2
      stages.type == ["one", "three"]
    }
  }

  @Unroll
  def "can add a synthetic stage #position"() {
    given:
    def pipeline = Pipeline
      .builder()
      .withApplication("orca")
      .withName("dummy-pipeline")
      .withStage("one")
      .withStage("two")
      .withStage("three")
      .build()

    repository.store(pipeline)

    expect:
    repository.retrievePipeline(pipeline.id).stages.size() == 3

    when:
    def stage = newStage(pipeline, "whatever", "two-whatever", [:], pipeline.namedStage("two"), position)
    repository.addStage(stage)

    then:
    with(repository.retrievePipeline(pipeline.id)) {
      stages.size() == 4
      stages.name == expectedStageNames
    }

    where:
    position     | expectedStageNames
    STAGE_BEFORE | ["one", "two-whatever", "two", "three"]
    STAGE_AFTER  | ["one", "two", "two-whatever", "three"]
  }

  def "can concurrently add stages without overwriting"() {
    given:
    def pipeline = Pipeline
      .builder()
      .withApplication("orca")
      .withName("dummy-pipeline")
      .withStage("one")
      .withStage("two")
      .withStage("three")
      .build()

    repository.store(pipeline)

    expect:
    repository.retrievePipeline(pipeline.id).stages.size() == 3

    when:
    def stage1 = newStage(pipeline, "whatever", "one-whatever", [:], pipeline.namedStage("one"), STAGE_BEFORE)
    def stage2 = newStage(pipeline, "whatever", "two-whatever", [:], pipeline.namedStage("two"), STAGE_BEFORE)
    def stage3 = newStage(pipeline, "whatever", "three-whatever", [:], pipeline.namedStage("three"), STAGE_BEFORE)
    def startLatch = new CountDownLatch(1)
    def doneLatch = new CountDownLatch(3)
    [stage1, stage2, stage3].each { stage ->
      Thread.start {
        startLatch.await(1, SECONDS)
        repository.addStage(stage)
        doneLatch.countDown()
      }
    }
    startLatch.countDown()

    then:
    doneLatch.await(1, SECONDS)

    and:
    with(repository.retrievePipeline(pipeline.id)) {
      stages.size() == 6
      stages.name == ["one-whatever", "one", "two-whatever", "two", "three-whatever", "three"]
    }
  }

  def "can save a stage with all data"() {
    given:
    def pipeline = Pipeline
      .builder()
      .withApplication("orca")
      .withName("dummy-pipeline")
      .withStage("one")
      .build()

    repository.store(pipeline)

    def stage = newStage(pipeline, "whatever", "one-whatever", [:], pipeline.namedStage("one"), STAGE_BEFORE)
    stage.lastModified = new Stage.LastModifiedDetails(user: "rfletcher@netflix.com", allowedAccounts: ["whatever"], lastModifiedTime: System.currentTimeMillis())
    stage.startTime = System.currentTimeMillis()
    stage.endTime = System.currentTimeMillis()
    stage.refId = "1<1"

    when:
    repository.storeStage(stage)

    then:
    notThrown(Exception)
  }
}
