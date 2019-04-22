/*
 * Copyright 2019 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package com.netflix.spinnaker.orca.igor.pipeline;

import com.netflix.spinnaker.orca.CancellableStage;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.igor.model.CIStageDefinition;
import com.netflix.spinnaker.orca.igor.tasks.*;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.tasks.artifacts.BindProducedArtifactsTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
public abstract class CIStage implements StageDefinitionBuilder, CancellableStage {
  private final StopJenkinsJobTask stopJenkinsJobTask;

  @Override
  public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    CIStageDefinition stageDefinition = stage.mapTo(CIStageDefinition.class);
    String jobType = StringUtils.capitalize(getType());
    builder
      .withTask(String.format("start%sJob", jobType), StartJenkinsJobTask.class)
      .withTask(String.format("waitFor%sJobStart", jobType), waitForJobStartTaskClass());

    if (stageDefinition.isWaitForCompletion()) {
      builder.withTask(String.format("monitor%sJob", jobType), MonitorJenkinsJobTask.class);
      builder.withTask("getBuildProperties", GetBuildPropertiesTask.class);
      builder.withTask("getBuildArtifacts", GetBuildArtifactsTask.class);
    }
    if (stageDefinition.getExpectedArtifacts().size() > 0) {
      builder.withTask(BindProducedArtifactsTask.TASK_NAME, BindProducedArtifactsTask.class);
    }
  }

  protected Class<? extends Task> waitForJobStartTaskClass() {
    return MonitorQueuedJenkinsJobTask.class;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void prepareStageForRestart(@Nonnull Stage stage) {
    Object buildInfo = stage.getContext().get("buildInfo");
    if (buildInfo != null) {
      Map<String, Object> restartDetails = (Map<String, Object>) stage.getContext()
        .computeIfAbsent("restartDetails", k -> new HashMap<String, Object>());
      restartDetails.put("previousBuildInfo", buildInfo);
    }
    stage.getContext().remove("buildInfo");
    stage.getContext().remove("buildNumber");
  }

  @Override
  public Result cancel(final Stage stage) {
    log.info(String.format(
      "Cancelling stage (stageId: %s, executionId: %s context: %s)",
      stage.getId(),
      stage.getExecution().getId(),
      stage.getContext()
    ));

    try {
      stopJenkinsJobTask.execute(stage);
    } catch (Exception e) {
      log.error(
        String.format("Failed to cancel stage (stageId: %s, executionId: %s), e: %s", stage.getId(), stage.getExecution().getId(), e.getMessage()),
        e
      );
    }
    return new Result(stage, new HashMap());
  }

  @Override
  public void onFailureStages(@Nonnull Stage stage, @Nonnull StageGraphBuilder graph) {
    CIStageDefinition stageDefinition = stage.mapTo(CIStageDefinition.class);
    if (stageDefinition.getPropertyFile() != null && !stageDefinition.getPropertyFile().equals("")) {
      log.info(
        "Stage failed (stageId: {}, executionId: {}), trying to find requested property file in case it was archived.",
        stage.getId(),
        stage.getExecution().getId()
      );
      graph.add( (Stage s) -> {
          s.setType(new GetPropertiesStage().getType());
          s.setName("Try to get properties file");
          Map<String, Object> context = new HashMap<>();
          context.put("master", stageDefinition.getMaster());
          context.put("job", stageDefinition.getJob());
          context.put("propertyFile", stageDefinition.getPropertyFile());
          context.put("buildNumber", stageDefinition.getBuildNumber());
          s.setContext(context);
        }
      );
    }
  }
}
