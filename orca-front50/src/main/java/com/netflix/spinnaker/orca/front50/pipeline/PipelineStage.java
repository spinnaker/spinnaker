/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.front50.pipeline;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static java.lang.String.format;
import static java.util.Collections.emptyMap;

import com.netflix.spinnaker.orca.api.pipeline.CancellableStage;
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.StageGraphBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.front50.tasks.MonitorPipelineTask;
import com.netflix.spinnaker.orca.front50.tasks.StartPipelineTask;
import com.netflix.spinnaker.orca.pipeline.model.StageContext;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.tasks.artifacts.BindProducedArtifactsTask;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PipelineStage implements StageDefinitionBuilder, CancellableStage {

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Value("${stages.pipeline.defaultSkipDownstreamOutput:false}")
  private boolean defaultSkipDownstreamOutput;

  public static final String PIPELINE_CONFIG_TYPE =
      StageDefinitionBuilder.getType(PipelineStage.class);

  final ExecutionRepository executionRepository;

  @Autowired
  public PipelineStage(ExecutionRepository executionRepository) {
    this.executionRepository = executionRepository;
  }

  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    // only start the pipeline if no execution ID already exists
    if (stage.getContext().get("executionId") == null) {
      builder.withTask("startPipeline", StartPipelineTask.class);
    }

    if (!stage
        .getContext()
        .getOrDefault("waitForCompletion", "true")
        .toString()
        .toLowerCase()
        .equals("false")) {
      builder.withTask("monitorPipeline", MonitorPipelineTask.class);
    }

    if (stage.getContext().containsKey("expectedArtifacts")) {
      builder.withTask(BindProducedArtifactsTask.TASK_NAME, BindProducedArtifactsTask.class);
    }
  }

  @Override
  public void afterStages(@Nonnull StageExecution stage, @Nonnull StageGraphBuilder graph) {
    if (shouldSkipDownstreamOutput(stage)) {
      stage.setOutputs(emptyMap());
    }
  }

  @Override
  public void prepareStageForRestart(@Nonnull StageExecution stage) {
    StageContext context = (StageContext) stage.getContext();

    context.remove("status");

    boolean skipPipelineRestart = (boolean) context.getCurrentOnly("_skipPipelineRestart", false);
    if (!skipPipelineRestart) {
      stage.getContext().remove("executionName");
      stage.getContext().remove("executionId");
    } else {
      // Keep the execution details in case the inner pipeline got restarted
      // Clear the skip restart flag
      stage.getContext().remove("_skipPipelineRestart");
    }
  }

  @Override
  public CancellableStage.Result cancel(StageExecution stage) {
    String readableStageDetails =
        format(
            "(stageId: %s, executionId: %s, context: %s)",
            stage.getId(), stage.getExecution().getId(), stage.getContext());
    log.info(format("Cancelling stage %s", readableStageDetails));

    try {
      String executionId = (String) stage.getContext().get("executionId");
      if (executionId != null) {
        if (executionRepository == null) {
          log.error(
              format(
                  "Stage %s could not be canceled w/o front50 enabled. Please set 'front50.enabled: true' in your orca config.",
                  readableStageDetails));
        } else {
          PipelineExecution childPipeline = executionRepository.retrieve(PIPELINE, executionId);
          if (!childPipeline.isCanceled()) {
            // flag the child pipeline as canceled (actual cancellation will happen asynchronously)
            executionRepository.cancel(
                stage.getExecution().getType(), executionId, "parent pipeline", null);
          }
        }
      }
    } catch (Exception e) {
      log.error(
          format("Failed to cancel stage %s, e: %s", readableStageDetails, e.getMessage()), e);
    }

    return new CancellableStage.Result(stage, emptyMap());
  }

  @Override
  public boolean canManuallySkip(StageExecution stage) {
    return (Boolean) stage.getContext().getOrDefault("skippable", false);
  }

  private boolean shouldSkipDownstreamOutput(StageExecution stage) {
    return stage
        .getContext()
        .getOrDefault("skipDownstreamOutput", defaultSkipDownstreamOutput)
        .toString()
        .equals("true");
  }
}
