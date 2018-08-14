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

import com.netflix.spinnaker.orca.CancellableStage;
import com.netflix.spinnaker.orca.front50.tasks.MonitorPipelineTask;
import com.netflix.spinnaker.orca.front50.tasks.StartPipelineTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.tasks.artifacts.BindProducedArtifactsTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE;
import static java.lang.String.format;
import static java.util.Collections.emptyMap;

@Component
public class PipelineStage implements StageDefinitionBuilder, CancellableStage {

  private final Logger log = LoggerFactory.getLogger(getClass());

  public static final String PIPELINE_CONFIG_TYPE = StageDefinitionBuilder.getType(PipelineStage.class);

  @Autowired(required = false)
  ExecutionRepository executionRepository;

  @Override
  public void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder
      .withTask("startPipeline", StartPipelineTask.class);

    if (!stage.getContext().getOrDefault("waitForCompletion", "true").toString().toLowerCase().equals("false")) {
      builder.withTask("monitorPipeline", MonitorPipelineTask.class);
    }

    if (stage.getContext().containsKey("expectedArtifacts")) {
      builder.withTask(BindProducedArtifactsTask.TASK_NAME, BindProducedArtifactsTask.class);
    }
  }

  @Override
  public void prepareStageForRestart(Stage stage) {
    stage.getContext().remove("status");
    stage.getContext().remove("executionName");
    stage.getContext().remove("executionId");
  }

  @Override
  public CancellableStage.Result cancel(Stage stage) {
    String readableStageDetails = format("(stageId: %s, executionId: %s, context: %s)", stage.getId(), stage.getExecution().getId(), stage.getContext());
    log.info(format("Cancelling stage %s", readableStageDetails));

    try {
      String executionId = (String) stage.getContext().get("executionId");
      if (executionId != null) {
        if (executionRepository == null) {
          log.error(format("Stage %s could not be canceled w/o front50 enabled. Please set 'front50.enabled: true' in your orca config.", readableStageDetails));
        } else {
          Execution childPipeline = executionRepository.retrieve(PIPELINE, executionId);
          if (!childPipeline.isCanceled()) {
            // flag the child pipeline as canceled (actual cancellation will happen asynchronously)
            executionRepository.cancel(stage.getExecution().getType(), executionId, "parent pipeline", null);
          }
        }
      }
    } catch (Exception e) {
      log.error(format("Failed to cancel stage %s, e: %s", readableStageDetails, e.getMessage()), e);
    }

    return new CancellableStage.Result(stage, emptyMap());
  }
}
