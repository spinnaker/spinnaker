/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.pipeline.model;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Internal helper methods for {@link StageExecution}. */
class StageExecutionInternals {

  /**
   * Worker method to get the list of all ancestors (parents and, optionally, prerequisite stages)
   * of the current stage.
   *
   * @param visited list of visited nodes
   * @param directParentOnly true to only include direct parents of the stage, false to also include
   *     stages this stage depends on (via requisiteRefIds)
   * @return list of ancestor stages
   */
  static List<StageExecution> getAncestorsImpl(
      StageExecution stage, Set<String> visited, boolean directParentOnly) {
    visited.add(stage.getRefId());

    if (!directParentOnly && !stage.getRequisiteStageRefIds().isEmpty()) {
      // Get stages this stage depends on via requisiteStageRefIds:
      Collection<String> requisiteStageRefIds = stage.getRequisiteStageRefIds();
      List<StageExecution> previousStages =
          stage.getExecution().getStages().stream()
              .filter(it -> !visited.contains(it.getRefId()))
              .filter(it -> requisiteStageRefIds.contains(it.getRefId()))
              .collect(toList());
      List<StageExecution> syntheticStages =
          stage.getExecution().getStages().stream()
              .filter(s -> s.getSyntheticStageOwner() != null)
              .filter(
                  s ->
                      previousStages.stream()
                          .map(StageExecution::getId)
                          .anyMatch(id -> id.equals(s.getParentStageId())))
              .collect(toList());
      return ImmutableList.<StageExecution>builder()
          .addAll(previousStages)
          .addAll(syntheticStages)
          .addAll(
              previousStages.stream()
                  .flatMap(it -> getAncestorsImpl(it, visited, directParentOnly).stream())
                  .collect(toList()))
          .build();
    } else if (stage.getParentStageId() != null && !visited.contains(stage.getParentStageId())) {
      // Get parent stages, but exclude already visited ones:

      List<StageExecution> ancestors = new ArrayList<>();
      if (stage.getSyntheticStageOwner() == SyntheticStageOwner.STAGE_AFTER) {
        ancestors.addAll(
            stage.getExecution().getStages().stream()
                .filter(
                    it ->
                        stage.getParentStageId().equals(it.getParentStageId())
                            && it.getSyntheticStageOwner() == SyntheticStageOwner.STAGE_BEFORE)
                .collect(toList()));
      }

      ancestors.addAll(
          stage.getExecution().getStages().stream()
              .filter(it -> it.getId().equals(stage.getParentStageId()))
              .findFirst()
              .<List<StageExecution>>map(
                  parent ->
                      ImmutableList.<StageExecution>builder()
                          .add(parent)
                          .addAll(getAncestorsImpl(parent, visited, directParentOnly))
                          .build())
              .orElse(emptyList()));

      return ancestors;
    } else {
      return emptyList();
    }
  }
}
