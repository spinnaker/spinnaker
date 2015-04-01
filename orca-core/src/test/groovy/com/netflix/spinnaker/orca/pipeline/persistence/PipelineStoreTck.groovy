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

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import spock.lang.Specification
import spock.lang.Subject

@Subject(ExecutionStore)
abstract class PipelineStoreTck<T extends ExecutionStore> extends Specification {

  @Subject T pipelineStore

  void setup() {
    pipelineStore = createPipelineStore()
  }

  abstract T createPipelineStore()

  def "if a pipeline does not have an id it is assigned one when stored"() {
    given:
    def pipeline = new Pipeline()

    expect:
    pipeline.id == null

    when:
    pipelineStore.store(pipeline)

    then:
    pipeline.id != null
  }

  def "if a pipeline already has an id it is not re-assigned when stored"() {
    given:
    def pipeline = new Pipeline(id: "a-preassigned-id")

    when:
    pipelineStore.store(pipeline)

    then:
    pipeline.id == old(pipeline.id)
  }

  def "a pipeline can be retrieved after being stored"() {
    given:
    def application = "orca"
    def pipeline = Pipeline.builder()
                           .withApplication(application)
                           .withName("dummy-pipeline")
                           .withTrigger(name: "some-jenkins-job", lastBuildLabel: 1)
                           .withStage("one", "one", [foo: "foo"])
                           .withStage("two", "two", [bar: "bar"])
                           .withStage("three", "three", [baz: "baz"])
                           .build()

    and:
    pipelineStore.store(pipeline)

    expect:
    pipelineStore.allForApplication(application).id == [pipeline.id]

    with(((Pipeline)pipelineStore.retrieve(pipeline.id))) {
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
  }

  def "a pipeline has correctly ordered stages after load"() {
    given:
    def pipeline = Pipeline.builder()
      .withStage("one", "one", [:])
      .withStage("two", "two", [:])
      .withStage("one-a", "one-1", [:])
      .withStage("one-b", "one-1", [:])
      .withStage("one-a-a", "three", [:])
      .build()

    def one = pipeline.stages.find { it.type == "one"}
    def oneA = pipeline.stages.find { it.type == "one-a"}
    def oneAA = pipeline.stages.find { it.type == "one-a-a"}
    def oneB = pipeline.stages.find { it.type == "one-b"}
    oneA.parentStageId = one.id
    oneAA.parentStageId = oneA.id
    oneB.parentStageId = one.id

    and:
    pipelineStore.store(pipeline)

    expect:
    with(((Pipeline)pipelineStore.retrieve(pipeline.id))) {
      stages*.type == ["one", "one-a", "one-a-a", "one-b", "two"]
    }
  }

  def "trying to retrieve an invalid id throws an exception"() {
    when:
    pipelineStore.retrieve("invalid")

    then:
    thrown ExecutionNotFoundException
  }

  def "trying to delete a non-existent id does not throw an exception"() {
    when:
    pipelineStore.delete("invalid")

    then:
    notThrown ExecutionNotFoundException
  }

  def "deleting a pipeline removes pipeline and stages"() {
    given:
    def application = "someApp"
    def pipeline = Pipeline.builder()
      .withStage("one", "one", [:])
      .withStage("two", "two", [:])
      .withStage("one-a", "one-1", [:])
      .withStage("one-b", "one-1", [:])
      .withStage("one-a-a", "three", [:])
      .withApplication(application)
      .build()

    def one = pipeline.stages.find { it.type == "one"}
    def oneA = pipeline.stages.find { it.type == "one-a"}
    def oneAA = pipeline.stages.find { it.type == "one-a-a"}
    def oneB = pipeline.stages.find { it.type == "one-b"}

    and:
    pipelineStore.store(pipeline)
    pipelineStore.delete(pipeline.id)

    expect:
    pipelineStore.retrieveStage(one.id) == null
    pipelineStore.retrieveStage(oneA.id) == null
    pipelineStore.retrieveStage(oneAA.id) == null
    pipelineStore.retrieveStage(oneB.id) == null

    when:
    pipelineStore.retrieve(pipeline.id)

    then:
    thrown ExecutionNotFoundException

    when:
    def allForApplication = pipelineStore.allForApplication(application)
    def allJobs = pipelineStore.all()

    then:
    allForApplication == []
    allJobs == []
  }
}
