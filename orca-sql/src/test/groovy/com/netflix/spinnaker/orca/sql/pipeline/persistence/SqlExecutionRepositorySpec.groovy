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
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.TestDatabase
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepositoryTck
import org.jooq.impl.DSL
import rx.schedulers.Schedulers
import de.huxhorn.sulky.ulid.ULID
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Unroll

import static com.netflix.spinnaker.kork.sql.test.SqlTestUtil.initPreviousTcMysqlDatabase
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import static com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator.BUILD_TIME_ASC
import static com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator.BUILD_TIME_DESC
import static com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.orchestration
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import static com.netflix.spinnaker.kork.sql.test.SqlTestUtil.initTcMysqlDatabase

class SqlExecutionRepositorySpec extends ExecutionRepositoryTck<ExecutionRepository> {

  @Shared
  ObjectMapper mapper = OrcaObjectMapper.newInstance().with {
    registerModule(new KotlinModule())
    it
  }

  def ulid = new ULID()

  @Shared
  @AutoCleanup("close")
  TestDatabase currentDatabase

  @Shared
  @AutoCleanup("close")
  TestDatabase previousDatabase

  def setupSpec() {
    currentDatabase = initTcMysqlDatabase()
    previousDatabase = initPreviousTcMysqlDatabase()
  }

  def cleanup() {
    cleanupDb(currentDatabase.context)
    cleanupDb(previousDatabase.context)
  }

  @Override
  ExecutionRepository createExecutionRepository() {
    return createExecutionRepository("test")
  }

  @Override
  ExecutionRepository createExecutionRepositoryPrevious() {
    new SqlExecutionRepository("test", previousDatabase.context, mapper, new RetryProperties(), 10, 100, "poolName", null)
  }

  ExecutionRepository createExecutionRepository(String partition) {
    return com.netflix.spinnaker.kork.telemetry.InstrumentedProxy.proxy(
        new DefaultRegistry(),
        new SqlExecutionRepository(partition, currentDatabase.context, mapper, new RetryProperties(), 10, 100, "poolName", null),
        "namespace")
  }

  def "can store a new pipeline"() {
    given:
    ExecutionRepository repo = createExecutionRepository()
    Execution e = new Execution(PIPELINE, "myapp")
    e.stages.add(new Stage(e, "wait", "wait stage", [foo: 'FOO']))

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
    Execution e = new Execution(PIPELINE, "myapp")
    e.stages.add(new Stage(e, "wait", "wait stage", [foo: 'FOO']))
    e.partition = "foreign"

    when:
    repo.store(e)

    then:
    thrown(ForeignExecutionException)
  }

  def "persists foreign executions when own partition is not set"() {
    given:
    ExecutionRepository repo = createExecutionRepository(null)
    Execution e = new Execution(PIPELINE, "myapp")
    e.stages.add(new Stage(e, "wait", "wait stage", [foo: 'FOO']))
    e.partition = "foreign"

    when:
    repo.store(e)

    then:
    noExceptionThrown()
  }

  def "fails to operate on foreign executions"() {
    given:
    ExecutionRepository repo = createExecutionRepository()
    Execution e = new Execution(PIPELINE, "myapp")
    e.stages.add(new Stage(e, "wait", "wait stage", [foo: 'FOO']))
    repo.store(e)

    currentDatabase.context
      .update(DSL.table("pipelines"))
      .set(DSL.field("`partition`"), DSL.value("foreign"))
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
  }

  def "can store a pipeline with a provided ULID"() {
    given:
    def id = ulid.nextULID()
    ExecutionRepository repo = createExecutionRepository()
    Execution e = new Execution(PIPELINE, id, "myapp")
    e.stages.add(new Stage(e, "wait", "wait stage", [foo: 'FOO']))

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
    Execution e = new Execution(PIPELINE, id, "myapp")
    e.stages.add(new Stage(e, "wait", "wait stage", [foo: 'FOO']))

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
    Execution e = new Execution(PIPELINE, id, "myapp")
    Stage s = new Stage(e, "wait", "wait stage", [foo: 2])
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
    Execution orig = new Execution(PIPELINE, id, "myapp")
    orig.stages.add(new Stage(orig, "wait", "wait stage 0", [foo: 'FOO']))
    repo.store(orig)

    when:
    Execution e = repo.retrieve(PIPELINE, id)

    then:
    e.id == id
    e.stages.size() == 1
    e.stages[0].context.foo == 'FOO'
  }

  def "can load a pipeline by UUID"() {
    given:
    def id = UUID.randomUUID().toString()
    ExecutionRepository repo = createExecutionRepository()
    Execution orig = new Execution(PIPELINE, id, "myapp")
    orig.stages.add(new Stage(orig, "wait", "wait stage 0", [foo: 'FOO']))
    repo.store(orig)

    when:
    Execution e = repo.retrieve(PIPELINE, id)

    then:
    e.id == id
    e.stages.size() == 1
    e.stages[0].id == orig.stages[0].id
    e.stages[0].context.foo == 'FOO'
  }

  def "can delete a pipeline"() {
    given:
    ExecutionRepository repo = createExecutionRepository()
    Execution orig = new Execution(PIPELINE, "myapp")
    orig.stages.add(new Stage(orig, "wait", "wait stage 0", [foo: 'FOO']))
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
    Execution orig = new Execution(PIPELINE, id, "myapp")
    orig.stages.add(new Stage(orig, "wait", "wait stage 0", [foo: 'FOO']))
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
    Execution orig = new Execution(PIPELINE, "myapp")
    orig.stages.add(new Stage(orig, "wait", "wait stage", [foo: 'FOO']))
    repo.store(orig)

    when:
    Execution e = new Execution(PIPELINE, orig.id, "myapp")
    e.stages.add(new Stage(e, "wait", "wait stage 1", [foo: 'FOO']))
    e.stages.add(new Stage(e, "wait", "wait stage 2", [bar: 'BAR']))
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
    Execution orig = new Execution(PIPELINE, id, "myapp")
    orig.stages.add(new Stage(orig, "wait", "wait stage", [foo: 'FOO']))
    repo.store(orig)

    when:
    Execution e = new Execution(PIPELINE, id, "myapp")
    e.stages.add(new Stage(e, "wait", "wait stage 1", [foo: 'FOO']))
    e.stages.add(new Stage(e, "wait", "wait stage 2", [bar: 'BAR']))
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
    Execution orig = new Execution(PIPELINE, "myapp")
    orig.stages.add(new Stage(orig, "wait", "wait stage", [foo: 'FOO']))
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
    (storeLimit + 1).times { i ->
      repository.store(pipeline {
        application = "spinnaker"
        pipelineConfigId = "foo1"
        name = "Execution #${i + 1}"
        buildTime = i + 1
      })

      repository.store(pipeline {
        application = "spinnaker"
        pipelineConfigId = "foo2"
        name = "Execution #${i + 1}"
        buildTime = i + 1
      })
    }

    when:
    def results = repository
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
    storeLimit = 6
    retrieveLimit = 10
  }

  def "can retrieve ALL pipelines by configIds between build time boundaries"() {
    given:
    (storeLimit + 1).times { i ->
      repository.store(pipeline {
        application = "spinnaker"
        pipelineConfigId = "foo1"
        name = "Execution #${i + 1}"
        buildTime = i + 1
      })

      repository.store(pipeline {
        application = "spinnaker"
        pipelineConfigId = "foo2"
        name = "Execution #${i + 1}"
        buildTime = i + 1
      })
    }

    when:
    List<Execution> forwardResults = repository
      .retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
      ["foo1", "foo2"],
      0L,
      5L,
      new ExecutionCriteria().setPageSize(1).setSortType(BUILD_TIME_ASC)
    )
    List<Execution> backwardsResults = repository
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
    storeLimit = 6
  }

  def "doesn't fail on empty configIds"() {
    expect:
    repository
      .retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
      [],
      0L,
      5L,
      new ExecutionCriteria().setPageSize(1).setSortType(BUILD_TIME_ASC)
    ).size() == 0
  }
}
