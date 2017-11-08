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
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Provides an enhanced version of {@link Stage#ancestors()} that returns tuples
 * of the ancestor stages and their {@link StageDefinitionBuilder}s.
 */
@Component
@CompileStatic
class StageNavigator {
  private final Map<String, StageDefinitionBuilder> stageDefinitionBuilders

  @Autowired
  StageNavigator(Collection<StageDefinitionBuilder> stageDefinitionBuilders) {
    this.stageDefinitionBuilders = stageDefinitionBuilders.collectEntries {
      [(it.type): it]
    }
  }

  /**
   * As per `Stage.ancestors` except this method returns tuples of the stages
   * and their `StageDefinitionBuilder`.
   */
  List<Result> ancestors(Stage startingStage) {
    startingStage.ancestors().collect {
      new Result(it, stageDefinitionBuilders[it.type])
    }
  }

  @Canonical
  static class Result {
    Stage stage
    StageDefinitionBuilder stageBuilder
  }
}
