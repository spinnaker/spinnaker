/*
 * Copyright 2015 Netflix, Inc.
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


package com.netflix.spinnaker.orca.pipeline.util

import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class StageNavigatorSpec extends Specification {

  @Shared
  def stageBuilders = [
    new ExampleStageBuilder("One"),
    new ExampleStageBuilder("Two"),
    new ExampleStageBuilder("Three")
  ]

  @Subject
  def stageNavigator = new StageNavigator(null) {
    @Override
    protected Collection<StageDefinitionBuilder> stageBuilders() {
      stageBuilders
    }
  }

  def execution = new Pipeline()

  def "traverses up the synthetic stage hierarchy"() {
    given:
    def stage1 = buildStage("One")
    def stage2 = buildStage("Two")
    def stage3 = buildStage("Three")

    stage1.parentStageId = stage2.id
    stage2.parentStageId = stage3.id

    expect:
    stage1.ancestors()*.stage*.type == ["One", "Two", "Three"]
    stage1.ancestors({ stage, builder -> false })*.stage*.type == []
    stage1.ancestors({ stage, builder -> true })*.stage*.type == ["One", "Two", "Three"]
    stage1.ancestors({ stage, builder -> stage.type == "One" })*.stage*.type == ["One"]
    stage1.ancestors({ stage, builder -> stage.type == "Four" })*.stage*.type == []
  }

  def "traverses up the refId stage hierarchy"() {
    given:
    def stage1 = buildStage("One")
    def stage2 = buildStage("Two")
    def stage4 = buildStage("Four")
    def stage3 = buildStage("Three")

    stage1.refId = "1"
    stage2.refId = "2"
    stage3.refId = "3"
    stage4.refId = "4"

    stage1.requisiteStageRefIds = ["2"]
    stage2.requisiteStageRefIds = ["3", "4"]

    expect:
    // order is dependent on the order of a stage within `execution.stages`
    stage1.ancestors()*.stage*.type == ["One", "Two", "Four", "Three"]
  }

  def "traverses up both synthetic and refId stage hierarchies"() {
    given:
    def stage1 = buildStage("One")
    def stage2 = buildStage("Two")
    def stage4 = buildStage("Four")
    def stage3 = buildStage("Three")

    stage1.refId = "1"
    stage2.refId = "2"
    stage3.refId = "3"
    stage4.refId = "4"

    stage1.requisiteStageRefIds = ["2"]
    stage2.parentStageId = stage3.id
    stage3.requisiteStageRefIds = ["4"]

    expect:
    // order is dependent on the order of a stage within `execution.stages`
    stage1.ancestors()*.stage*.type == ["One", "Two", "Three", "Four"]
  }

  private Stage buildStage(String type) {
    def pipelineStage = new Stage<>(execution, type)
    pipelineStage.stageNavigator = stageNavigator

    execution.stages << pipelineStage

    return pipelineStage
  }

  static class ExampleStageBuilder implements StageDefinitionBuilder {
    private final String type

    ExampleStageBuilder(String type) {
      this.type = type
    }

    @Override
    String getType() {
      return type
    }
  }

}
