/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.batch.stages;

import com.netflix.spinnaker.orca.DefaultTaskResult;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.batch.StageBuilder;
import com.netflix.spinnaker.orca.batch.StageBuilderProvider;
import com.netflix.spinnaker.orca.pipeline.BranchingStageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.flow.Flow;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Deprecated
public class ParallelDeployStageDefinitionBuilder extends ParallelStageDefinitionBuilder {
  public ParallelDeployStageDefinitionBuilder(BranchingStageDefinitionBuilder delegate,
                                              StageBuilderProvider stageBuilderProvider) {
    super(delegate, stageBuilderProvider);
  }

  @Override
  protected List<Flow> buildFlows(Stage stage) {
    return parallelContexts(stage)
      .stream()
      .map(context -> {
          Stage nextStage = newStage(
            stage.getExecution(),
            (String) context.get("type"),
            (String) context.get("name"),
            new HashMap(context),
            stage,
            SyntheticStageOwner.STAGE_AFTER
          );
          String nextStageId = nextStage.getId();

          List<Stage> existingStages = stage.getExecution().getStages();
          Optional<Stage> existingStage = existingStages.stream()
            .filter(s -> s.getId().equals(nextStageId))
            .findFirst();

          nextStage = existingStage.orElse(nextStage);
          if (!existingStage.isPresent()) {
            nextStage.setType(delegate.getChildStageType(stage));
            stage.getExecution().getStages().add(nextStage);
          }

          FlowBuilder flowBuilder = new FlowBuilder<Flow>(context.get("name").toString()).start(
            buildStep(stage, "setupParallelDeploy", new Task() {
              @Override
              public TaskResult execute(Stage ignored) {
                return new DefaultTaskResult(ExecutionStatus.SUCCEEDED);
              }
            })
          );


          StageBuilder stageBuilder = stageBuilderProvider.all().stream()
            .filter(it -> it.getType().equals(context.get("type").toString()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No stage provided found for " + context.get("type")));
          stageBuilder.build(flowBuilder, nextStage);
          return (Flow) flowBuilder.end();
        }
      ).collect(Collectors.toList());
  }
}
