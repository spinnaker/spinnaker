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

import java.util.function.BiFunction
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

@Component
@CompileStatic
class StageNavigator {
  private final ApplicationContext applicationContext

  @Autowired
  StageNavigator(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext
  }

  public <T extends Execution<T>> List<Result> findAll(Stage startingStage, BiFunction<Stage<T>, StageDefinitionBuilder, Boolean> matcher) {
    def stageBuilders = stageBuilders()

    def ancestors = [startingStage] + ancestors(startingStage)
    def results = ancestors.findAll { Stage stage ->
      def stageBuilder = stageBuilders.find { it.type == stage.type }
      return matcher.apply(stage, stageBuilder)
    }.collect { Stage stage ->
      new Result(stage, stageBuilders.find { it.type == stage.type })
    }

    return results
  }

  protected Collection<StageDefinitionBuilder> stageBuilders() {
    return applicationContext.getBeansOfType(StageDefinitionBuilder).values()
  }

  private List<Stage> ancestors(Stage startingStage) {
    if (startingStage.requisiteStageRefIds) {
      def previousStages = startingStage.execution.stages.findAll {
        it.refId in startingStage.requisiteStageRefIds
      }
      def syntheticStages = startingStage.execution.stages.findAll {
        it.parentStageId in previousStages*.id
      }
      return ((previousStages + syntheticStages) + previousStages.collect { ancestors(it) }.flatten()) as List<Stage>
    } else if (startingStage.parentStageId) {
      def parent = startingStage.execution.stages.find { it.id == startingStage.parentStageId }
      return (([parent] + ancestors(parent)).flatten()) as List<Stage>
    } else {
      return []
    }
  }


  @Canonical
  static class Result {
    final Stage stage
    final StageDefinitionBuilder stageBuilder
  }
}
