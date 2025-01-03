/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.orca.sql.pipeline.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.config.ExecutionCompressionProperties
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.TestDatabase
import com.netflix.spinnaker.kork.telemetry.InstrumentedProxy
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.interlink.Interlink
import com.netflix.spinnaker.orca.interlink.events.CancelInterlinkEvent
import com.netflix.spinnaker.orca.interlink.events.DeleteInterlinkEvent
import com.netflix.spinnaker.orca.interlink.events.PauseInterlinkEvent
import com.netflix.spinnaker.orca.interlink.events.ResumeInterlinkEvent
import com.netflix.spinnaker.orca.interlink.events.PatchStageInterlinkEvent
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.PipelineExecutionRepositoryTck
import org.jooq.impl.DSL
import rx.schedulers.Schedulers
import de.huxhorn.sulky.ulid.ULID
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Subject
import spock.lang.Unroll

import javax.sql.DataSource

import static com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import static com.netflix.spinnaker.kork.sql.test.SqlTestUtil.initDualTcMysqlDatabases
import static com.netflix.spinnaker.kork.sql.test.SqlTestUtil.initDualTcPostgresDatabases
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import static com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator.BUILD_TIME_ASC
import static com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator.BUILD_TIME_DESC
import static com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.orchestration
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline

abstract class SqlPipelineExecutionRepositorySpec extends PipelineExecutionRepositoryTck<ExecutionRepository> {

  @Shared
  ObjectMapper mapper = OrcaObjectMapper.newInstance().with {
    registerModule(new KotlinModule.Builder().build())
    it
  }

  def ulid = new ULID()

  @Shared
  @Subject
  ExecutionRepository repository

  @Override
  ExecutionRepository repository() {
    return repository
  }

  @Shared
  @AutoCleanup("close")
  TestDatabase currentDatabase

  @Shared
  @AutoCleanup("close")
  TestDatabase previousDatabase

  abstract TestDatabase getDatabase()

  def setupSpec() {
    currentDatabase = getDatabase()
    repository = createExecutionRepository()
  }

  def cleanup() {
    cleanupDb(currentDatabase.context)
  }

  ExecutionRepository createExecutionRepository() {
    return createExecutionRepository("test")
  }

  ExecutionRepository createExecutionRepository(String partition, Interlink interlink = null, boolean compression = false) {
    return InstrumentedProxy.proxy(
        new DefaultRegistry(),
        new SqlExecutionRepository(partition,
            currentDatabase.context,
            mapper,
            new RetryProperties(),
            10,
            100,
            "poolName",
            "readPoolName",
            interlink,
            [],
            new ExecutionCompressionProperties(enabled: compression),
            false,
            Mock(DataSource)),
        "namespace")
  }

  def "can store a new pipeline"() {
    given:
    ExecutionRepository repo = createExecutionRepository()
    PipelineExecution e = new PipelineExecutionImpl(PIPELINE, "myapp")
    e.stages.add(new StageExecutionImpl(e, "wait", "wait stage", [foo: 'FOO']))

    when:
    repo.store(e)

    then:
    def pipelines = repo.retrieve(PIPELINE).toList().toBlocking().single()
    pipelines.size() == 1
    pipelines[0].id == e.id
    pipelines[0].stages.size() == 1
    pipelines[0].stages[0].id == e.stages[0].id
    pipelines[0].stages[0].context.foo == 'FOO'
    pipelines[0].partition == 'test'
  }

  def "fails to persist foreign executions"() {
    given:
    ExecutionRepository repo = createExecutionRepository()
    PipelineExecution e = new PipelineExecutionImpl(PIPELINE, "myapp")
    e.stages.add(new StageExecutionImpl(e, "wait", "wait stage", [foo: 'FOO']))
    e.partition = "foreign"

    when:
    repo.store(e)

    then:
    thrown(ForeignExecutionException)
  }

  def "persists foreign executions when own partition is not set"() {
    // Need to clear the DB here
    cleanup()

    given:
    ExecutionRepository repo = createExecutionRepository(null)
    PipelineExecution e = new PipelineExecutionImpl(PIPELINE, "myapp")
    e.stages.add(new StageExecutionImpl(e, "wait", "wait stage", [foo: 'FOO']))
    e.partition = "foreign"

    when:
    repo.store(e)

    then:
    noExceptionThrown()
  }

  def "fails to operate on foreign executions"() {
    given:
    ExecutionRepository repo = createExecutionRepository()
    PipelineExecution e = new PipelineExecutionImpl(PIPELINE, "myapp")
    e.stages.add(new StageExecutionImpl(e, "wait", "wait stage", [foo: 'FOO']))
    repo.store(e)

    currentDatabase.context
      .update(DSL.table("pipelines"))
      .set(DSL.field(DSL.name("partition")), DSL.value("foreign"))
      .execute()

    when:
    repo.pause(PIPELINE, e.id, "test@user.com")

    then:
    thrown(ForeignExecutionException)

    when:
    repo.resume(PIPELINE, e.id, "test@user.com")

    then:
    thrown(ForeignExecutionException)

    when:
    repo.cancel(PIPELINE, e.id)

    then:
    thrown(ForeignExecutionException)

    when:
    repo.delete(PIPELINE, e.id)

    then:
    thrown(ForeignExecutionException)

    when:
    StageExecution stage = e.stages.find()
    stage.context.putAll([skipRemainingWait: true])
    repo.storeStage(stage)

    then:
    thrown(ForeignExecutionException)
  }

  def "sends interlink events for foreign executions if available"() {
    given:
    Interlink interlink = Mock(Interlink)
    ExecutionRepository repo = createExecutionRepository("test", interlink)
    PipelineExecution e = new PipelineExecutionImpl(PIPELINE, "myapp")
    e.stages.add(new StageExecutionImpl(e, "wait", "wait stage", [foo: 'FOO']))
    repo.store(e)

    currentDatabase.context
        .update(DSL.table("pipelines"))
        .set(DSL.field(DSL.name("partition")), DSL.value("foreign"))
        .execute()

    when:
    repo.pause(PIPELINE, e.id, "test@user.com")

    then:
    1 * interlink.publish(_ as PauseInterlinkEvent)

    when:
    repo.resume(PIPELINE, e.id, "test@user.com")

    then:
    1 * interlink.publish(_ as ResumeInterlinkEvent)

    when:
    repo.cancel(PIPELINE, e.id)

    then:
    1 * interlink.publish(_ as CancelInterlinkEvent)

    when:
    repo.delete(PIPELINE, e.id)

    then:
    1 * interlink.publish(_ as DeleteInterlinkEvent)

    when:
    StageExecution stage = e.stages.find()
    stage.context.putAll([skipRemainingWait: true])
    repo.storeStage(stage)

    then:
    1 * interlink.publish(_ as PatchStageInterlinkEvent)
  }

  def "can store a pipeline with a provided ULID"() {
    given:
    def id = ulid.nextULID()
    ExecutionRepository repo = createExecutionRepository()
    PipelineExecution e = new PipelineExecutionImpl(PIPELINE, id, "myapp")
    e.stages.add(new StageExecutionImpl(e, "wait", "wait stage", [foo: 'FOO']))

    when:
    repo.store(e)

    then:
    def pipelines = repo.retrieve(PIPELINE).toList().toBlocking().single()
    pipelines.size() == 1
    pipelines[0].id == id
    pipelines[0].stages.size() == 1
    pipelines[0].stages[0].id == e.stages[0].id
    pipelines[0].stages[0].context.foo == 'FOO'
  }

  def "can store a pipeline with a provided UUID"() {
    given:
    def id = UUID.randomUUID().toString()
    ExecutionRepository repo = createExecutionRepository()
    PipelineExecution e = new PipelineExecutionImpl(PIPELINE, id, "myapp")
    e.stages.add(new StageExecutionImpl(e, "wait", "wait stage", [foo: 'FOO']))

    when:
    repo.store(e)

    then:
    def pipelines = repo.retrieve(PIPELINE).toList().toBlocking().single()
    pipelines.size() == 1
    pipelines[0].id == id
    pipelines[0].stages.size() == 1
    pipelines[0].stages[0].id == e.stages[0].id
    pipelines[0].stages[0].context.foo == 'FOO'
  }

  def "can store a pipeline with cyclic reference"() {
    given:
    def id = UUID.randomUUID().toString()
    ExecutionRepository repo = createExecutionRepository()
    PipelineExecution e = new PipelineExecutionImpl(PIPELINE, id, "myapp")
    StageExecution s = new StageExecutionImpl(e, "wait", "wait stage", [foo: 2])
    // try to create cyclic reference
    s.context.foo = s
    e.stages.add(s)

    when:
    repo.store(e)

    then:
    def pipelines = repo.retrieve(PIPELINE).toList().toBlocking().single()
    pipelines.size() == 1
    pipelines[0].id == id
    pipelines[0].stages.size() == 1
    pipelines[0].stages[0].id == e.stages[0].id
    pipelines[0].stages[0].context.foo == e.stages[0].id
  }

  def "can load a pipeline by ULID"() {
    given:
    def id = ulid.nextULID()
    ExecutionRepository repo = createExecutionRepository()
    PipelineExecution orig = new PipelineExecutionImpl(PIPELINE, id, "myapp")
    orig.stages.add(new StageExecutionImpl(orig, "wait", "wait stage 0", [foo: 'FOO']))
    repo.store(orig)

    when:
    PipelineExecution e = repo.retrieve(PIPELINE, id)

    then:
    e.id == id
    e.stages.size() == 1
    e.stages[0].context.foo == 'FOO'
  }

  def "can load a pipeline by UUID"() {
    given:
    def id = UUID.randomUUID().toString()
    ExecutionRepository repo = createExecutionRepository()
    PipelineExecution orig = new PipelineExecutionImpl(PIPELINE, id, "myapp")
    orig.stages.add(new StageExecutionImpl(orig, "wait", "wait stage 0", [foo: 'FOO']))
    repo.store(orig)

    when:
    PipelineExecution e = repo.retrieve(PIPELINE, id)

    then:
    e.id == id
    e.stages.size() == 1
    e.stages[0].id == orig.stages[0].id
    e.stages[0].context.foo == 'FOO'
  }

  def "can delete a pipeline"() {
    given:
    ExecutionRepository repo = createExecutionRepository()
    PipelineExecution orig = new PipelineExecutionImpl(PIPELINE, "myapp")
    orig.stages.add(new StageExecutionImpl(orig, "wait", "wait stage 0", [foo: 'FOO']))
    repo.store(orig)

    when:
    def pipelines = repo.retrieve(PIPELINE).toList().toBlocking().single()

    then:
    pipelines.size() == 1

    when:
    repo.delete(PIPELINE, orig.id)
    pipelines = repo.retrieve(PIPELINE).toList().toBlocking().single()

    then:
    pipelines.size() == 0
  }

  def "can delete a pipeline by UUID"() {
    given:
    def id = UUID.randomUUID().toString()
    ExecutionRepository repo = createExecutionRepository()
    PipelineExecution orig = new PipelineExecutionImpl(PIPELINE, id, "myapp")
    orig.stages.add(new StageExecutionImpl(orig, "wait", "wait stage 0", [foo: 'FOO']))
    repo.store(orig)

    when:
    def pipelines = repo.retrieve(PIPELINE).toList().toBlocking().single()

    then:
    pipelines.size() == 1

    when:
    repo.delete(PIPELINE, id)
    pipelines = repo.retrieve(PIPELINE).toList().toBlocking().single()

    then:
    pipelines.size() == 0
  }

  def "can update a pipeline"() {
    given:
    ExecutionRepository repo = createExecutionRepository()
    PipelineExecution orig = new PipelineExecutionImpl(PIPELINE, "myapp")
    orig.stages.add(new StageExecutionImpl(orig, "wait", "wait stage", [foo: 'FOO']))
    repo.store(orig)

    when:
    PipelineExecution e = new PipelineExecutionImpl(PIPELINE, orig.id, "myapp")
    e.stages.add(new StageExecutionImpl(e, "wait", "wait stage 1", [foo: 'FOO']))
    e.stages.add(new StageExecutionImpl(e, "wait", "wait stage 2", [bar: 'BAR']))
    repo.store(e)

    def pipelines = repo.retrieve(PIPELINE).toList().toBlocking().single()
    def storedStages = e.stages
    def loadedStages = pipelines*.stages.flatten()

    then:
    pipelines.size() == 1
    pipelines[0].id == orig.id
    loadedStages.size() == 2
    loadedStages*.id.sort() == storedStages*.id.sort()
    loadedStages*.name.sort() == storedStages*.name.sort()
  }

  def "can update a pipeline by UUID"() {
    given:
    def id = UUID.randomUUID().toString()
    ExecutionRepository repo = createExecutionRepository()
    PipelineExecution orig = new PipelineExecutionImpl(PIPELINE, id, "myapp")
    orig.stages.add(new StageExecutionImpl(orig, "wait", "wait stage", [foo: 'FOO']))
    repo.store(orig)

    when:
    PipelineExecution e = new PipelineExecutionImpl(PIPELINE, id, "myapp")
    e.stages.add(new StageExecutionImpl(e, "wait", "wait stage 1", [foo: 'FOO']))
    e.stages.add(new StageExecutionImpl(e, "wait", "wait stage 2", [bar: 'BAR']))
    repo.store(e)

    def pipelines = repo.retrieve(PIPELINE).toList().toBlocking().single()
    def storedStages = e.stages as Set
    def loadedStages = pipelines*.stages.flatten() as Set

    then:
    pipelines.size() == 1
    pipelines[0].id == id
    pipelines[0].stages.size() == 2
    loadedStages == storedStages
  }

  def "can update a stage"() {
    given:
    ExecutionRepository repo = createExecutionRepository()
    PipelineExecution orig = new PipelineExecutionImpl(PIPELINE, "myapp")
    orig.stages.add(new StageExecutionImpl(orig, "wait", "wait stage", [foo: 'FOO']))
    repo.store(orig)

    when:
    def stage = orig.stages[0]
    stage.name = "wait stage updated"

    repo.storeStage(stage)
    def pipelines = repo.retrieve(PIPELINE).toList().toBlocking().single()

    then:
    pipelines.size() == 1
    pipelines[0].id == orig.id
    pipelines[0].stages.size() == 1
    pipelines[0].stages[0].name == "wait stage updated"
  }

  def "no specified page retrieves first page"() {
    given:
    def criteria = new ExecutionCriteria().setPageSize(limit)

    and:
    (limit + 1).times { i ->
      repository.store(orchestration {
        application = "spinnaker"
        name = "Orchestration #${i + 1}"
      })
      // our ULID implementation isn't monotonic
      sleep(5)
    }

    when:
    def results = repository
      .retrieveOrchestrationsForApplication("spinnaker", criteria)
      .subscribeOn(Schedulers.immediate())
      .toList()
      .toBlocking()
      .first()

    then:
    with(results) {
      size() == criteria.pageSize
      first().name == "Orchestration #${limit + 1}"
      last().name == "Orchestration #2"
    }

    where:
    limit = 20
  }

  def "out of range page retrieves empty result set"() {
    given:
    def criteria = new ExecutionCriteria().setPageSize(limit).setPage(3)

    and:
    (limit + 1).times { i ->
      repository.store(orchestration {
        application = "spinnaker"
        name = "Orchestration #${i + 1}"
      })
    }

    when:
    def results = repository
      .retrieveOrchestrationsForApplication("spinnaker", criteria)
      .subscribeOn(Schedulers.immediate())
      .toList()
      .toBlocking()
      .first()

    then:
    results.isEmpty()

    where:
    limit = 20
  }

  def "page param > 1 retrieves the relevant page"() {
    given:
    def criteria = new ExecutionCriteria().setPageSize(limit).setPage(2)

    and:
    (limit + 1).times { i ->
      repository.store(orchestration {
        application = "spinnaker"
        name = "Orchestration #${i + 1}"
      })
      // our ULID implementation isn't monotonic
      sleep(5)
    }

    when:
    def results = repository
      .retrieveOrchestrationsForApplication("spinnaker", criteria)
      .subscribeOn(Schedulers.immediate())
      .toList()
      .toBlocking()
      .first()

    then:
    with(results) {
      size() == 1
      first().name == "Orchestration #1"
    }

    where:
    limit = 20
  }

  @Unroll
  def "page size param restricts the number of results"() {
    given:
    def criteria = new ExecutionCriteria().setPageSize(limit).setPage(page)

    and:
    executions.times { i ->
      repository.store(orchestration {
        application = "spinnaker"
        name = "Orchestration #${i + 1}"
      })
      // our ULID implementation isn't monotonic
      sleep(5)
    }

    when:
    def results = repository
      .retrieveOrchestrationsForApplication("spinnaker", criteria)
      .subscribeOn(Schedulers.immediate())
      .toList()
      .toBlocking()
      .first()

    then:
    with(results) {
      size() == expectedResults
      first().name == firstResult
      last().name == lastResult
    }

    where:
    executions | page | limit || expectedResults | firstResult         | lastResult
    21         | 2    | 5     || 5               | "Orchestration #16" | "Orchestration #12"
    21         | 5    | 5     || 1               | "Orchestration #1"  | "Orchestration #1"
    21         | 5    | 2     || 2               | "Orchestration #13" | "Orchestration #12"
  }

  def "can retrieve pipelines by configIds between build time boundaries"() {
    given:
    ExecutionRepository repo = createExecutionRepository("test", null, compressionEnabled)
    (storeLimit + 1).times { i ->
      repo.store(pipeline {
        application = "spinnaker"
        pipelineConfigId = "foo1"
        name = "Execution #${i + 1}"
        buildTime = i + 1
      })

      repo.store(pipeline {
        application = "spinnaker"
        pipelineConfigId = "foo2"
        name = "Execution #${i + 1}"
        buildTime = i + 1
      })
    }

    when:
    def results = repo
      .retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
      ["foo1", "foo2"],
      0L,
      6L,
      new ExecutionCriteria()
    )

    then:
    with(results) {
      size() == retrieveLimit
    }

    where:
    storeLimit  |  retrieveLimit | compressionEnabled
    6           | 10             | false
    6           | 10             | true
  }

  def "can retrieve ALL pipelines by configIds between build time boundaries"() {
    given:
    ExecutionRepository repo = createExecutionRepository("test", null, compressionEnabled)
    (storeLimit + 1).times { i ->
      repo.store(pipeline {
        application = "spinnaker"
        pipelineConfigId = "foo1"
        name = "Execution #${i + 1}"
        buildTime = i + 1
      })

      repo.store(pipeline {
        application = "spinnaker"
        pipelineConfigId = "foo2"
        name = "Execution #${i + 1}"
        buildTime = i + 1
      })
    }

    when:
    List<PipelineExecution> forwardResults = repo
      .retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
      ["foo1", "foo2"],
      0L,
      5L,
      new ExecutionCriteria().setPageSize(1).setSortType(BUILD_TIME_ASC)
    )
    List<PipelineExecution> backwardsResults = repo
      .retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
      ["foo1", "foo2"],
      0L,
      5L,
      new ExecutionCriteria().setPageSize(1).setSortType(BUILD_TIME_DESC)
    )

    then:
    forwardResults.size() == 8
    forwardResults.first().buildTime == 1
    backwardsResults.size() == 8
    backwardsResults.first().buildTime == 4


    where:
    storeLimit  | compressionEnabled
    6           | false
    6           | true
  }

  def "doesn't fail on empty configIds"() {
    given:
    ExecutionRepository repo = createExecutionRepository("test", null, compressionEnabled)

    expect:
    repo
      .retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
      [],
      0L,
      5L,
      new ExecutionCriteria().setPageSize(1).setSortType(BUILD_TIME_ASC)
    ).size() == 0

    where:
    compressionEnabled | _
    false              | _
    true               | _
  }
}

class MySqlPipelineExecutionRepositorySpec extends SqlPipelineExecutionRepositorySpec {
  @Override
  TestDatabase getDatabase() {
    return initDualTcMysqlDatabases()
  }
}

class PgSqlPipelineExecutionRepositorySpec extends SqlPipelineExecutionRepositorySpec {
  @Override
  TestDatabase getDatabase() {
    return initDualTcPostgresDatabases()
  }
}
