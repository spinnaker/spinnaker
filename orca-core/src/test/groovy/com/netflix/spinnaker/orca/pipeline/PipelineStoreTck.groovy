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

package com.netflix.spinnaker.orca.pipeline

import spock.lang.Specification
import spock.lang.Subject

abstract class PipelineStoreTck<T extends PipelineStore> extends Specification {

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
    def pipeline = Pipeline.builder()
                           .withApplication("orca")
                           .withName("dummy-pipeline")
                           .withStages("one", "two", "three")
                           .build()

    and:
    pipelineStore.store(pipeline)

    expect:
    with(pipelineStore.retrieve(pipeline.id)) {
      id == pipeline.id
      application == pipeline.application
      name == pipeline.name
      stages.type == pipeline.stages.type
      stages.pipeline.every {
        it.id == pipeline.id
      }
    }
  }
}
