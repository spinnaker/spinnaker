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
package com.netflix.spinnaker.orca.pipeline.persistence;

import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER;

import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

public class ExecutionRepositoryUtil {

  /**
   * Ensure proper stage ordering even when stageIndex is an unsorted set or absent.
   *
   * <p>Necessary for ensuring API responses and the UI render stages in the order of their
   * execution.
   */
  public static void sortStagesByReference(
      @Nonnull Execution execution, @Nonnull List<Stage> stages) {
    if (!execution.getStages().isEmpty()) {
      throw new StagesAlreadySorted();
    }

    if (stages.stream().map(Stage::getRefId).allMatch(Objects::nonNull)) {
      execution
          .getStages()
          .addAll(
              stages.stream()
                  .filter(s -> s.getParentStageId() == null)
                  .sorted(Comparator.comparing(Stage::getRefId))
                  .collect(Collectors.toList()));

      stages.stream()
          .filter(s -> s.getParentStageId() != null)
          .sorted(Comparator.comparing(Stage::getRefId))
          .forEach(
              s -> {
                Integer index = execution.getStages().indexOf(s.getParent());
                if (s.getSyntheticStageOwner() == STAGE_AFTER) {
                  index++;
                }
                execution.getStages().add(index, s);
              });
    } else {
      execution.getStages().addAll(stages);
    }
  }

  private static class StagesAlreadySorted extends IllegalStateException {
    StagesAlreadySorted() {
      super("Execution already has stages defined");
    }
  }
}
