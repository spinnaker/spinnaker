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

import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.JedisExecutionRepository
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.util.Pool
import spock.lang.*

@Subject(ExecutionRepository)
@Unroll
abstract class ExecutionRepositoryTck<T extends ExecutionRepository> extends Specification {

  @Subject T repository

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
    execution << [new Pipeline(), new Orchestration()]

  }

  def "if an execution already has an id it is not re-assigned when stored"() {
    when:
    repository.store(execution)

    then:
    execution.id == old(execution.id)

    where:
    execution << [new Pipeline(id: "a-preassigned-id"), new Orchestration(id: "a-preassigned-id")]
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

  def "a pipeline has correctly ordered stages after load"() {
    given:
    def pipeline = Pipeline
      .builder()
      .withStage("one", "one", [:])
      .withStage("two", "two", [:])
      .withStage("one-a", "one-1", [:])
      .withStage("one-b", "one-1", [:])
      .withStage("one-a-a", "three", [:])
      .build()

    def one = pipeline.stages.find { it.type == "one" }
    def oneA = pipeline.stages.find { it.type == "one-a" }
    def oneAA = pipeline.stages.find { it.type == "one-a-a" }
    def oneB = pipeline.stages.find { it.type == "one-b" }
    oneA.parentStageId = one.id
    oneAA.parentStageId = oneA.id
    oneB.parentStageId = one.id

    and:
    repository.store(pipeline)

    expect:
    with(repository.retrievePipeline(pipeline.id)) {
      stages*.type == ["one", "one-a", "one-a-a", "one-b", "two"]
    }
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
}

class JedisExecutionRepositorySpec extends ExecutionRepositoryTck<JedisExecutionRepository> {

  @Shared @AutoCleanup("destroy") EmbeddedRedis embeddedRedis

  def setupSpec() {
    embeddedRedis = EmbeddedRedis.embed()
  }

  def cleanup() {
    embeddedRedis.jedis.flushDB()
  }

  Pool<Jedis> jedisPool = new JedisPool("localhost", embeddedRedis.@port)

  @Override
  JedisExecutionRepository createExecutionRepository() {
    new JedisExecutionRepository(jedisPool, 1, 50)
  }
}
