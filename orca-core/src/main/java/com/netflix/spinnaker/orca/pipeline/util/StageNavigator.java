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

package com.netflix.spinnaker.orca.pipeline.util;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Provides an enhanced version of {@link Stage#ancestors()} that returns tuples
 * of the ancestor stages and their {@link StageDefinitionBuilder}s.
 */
@Component public class StageNavigator {
  private final Map<String, StageDefinitionBuilder> stageDefinitionBuilders;

  @Autowired
  public StageNavigator(Collection<StageDefinitionBuilder> stageDefinitionBuilders) {
    this.stageDefinitionBuilders = stageDefinitionBuilders
      .stream()
      .collect(toMap(StageDefinitionBuilder::getType, Function.identity()));
  }

  /**
   * As per `Stage.ancestors` except this method returns tuples of the stages
   * and their `StageDefinitionBuilder`.
   */
  public List<Result> ancestors(Stage startingStage) {
    return startingStage
      .ancestors()
      .stream()
      .map(it ->
        new Result(it, stageDefinitionBuilders.get(it.getType()))
      )
      .collect(toList());
  }

  public static class Result {
    private final Stage stage;
    private final StageDefinitionBuilder stageBuilder;

    Result(Stage stage, StageDefinitionBuilder stageBuilder) {
      this.stage = stage;
      this.stageBuilder = stageBuilder;
    }

    public Stage getStage() {
      return stage;
    }

    public StageDefinitionBuilder getStageBuilder() {
      return stageBuilder;
    }
  }
}
