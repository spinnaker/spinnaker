/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.orca.pipeline.tasks;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import javax.annotation.Nonnull;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
public class StageStatusPreconditionTask implements PreconditionTask {

  @Override
  public String getPreconditionType() {
    return "stageStatus";
  }

  @Override
  public @Nonnull TaskResult execute(@Nonnull Stage stage) {
    StageStatusPreconditionContext context =
        stage.mapTo("/context", StageStatusPreconditionContext.class);
    String stageName = context.getStageName();
    String assertedStatus = context.getStageStatus();
    if (stageName == null) {
      throw new IllegalArgumentException(
          String.format(
              "Stage name is required for preconditions of type %s.", getPreconditionType()));
    }
    if (assertedStatus == null) {
      throw new IllegalArgumentException(
          String.format(
              "Stage status is required for preconditions of type %s.", getPreconditionType()));
    }
    Stage foundStage =
        stage.getExecution().getStages().stream()
            .filter(s -> s.getName().equals(stageName))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        String.format(
                            "Failed to find stage %s in execution. Please specify a valid stage name",
                            stageName)));
    String actualStatus = foundStage.getStatus().toString();
    if (!actualStatus.equals(assertedStatus)) {
      throw new RuntimeException(
          String.format(
              "The status of stage %s was asserted to be %s, but was actually %s",
              stageName, assertedStatus, actualStatus));
    }
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).build();
  }

  @Getter
  private static class StageStatusPreconditionContext {
    public String stageName;
    public String stageStatus;
  }
}
