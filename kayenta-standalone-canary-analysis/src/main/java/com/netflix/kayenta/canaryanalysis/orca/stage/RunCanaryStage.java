/*
 * Copyright (c) 2018 Nike, inc.
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

package com.netflix.kayenta.canaryanalysis.orca.stage;

import com.netflix.spinnaker.orca.CancellableStage;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.kayenta.canaryanalysis.orca.task.MonitorCanaryTask;
import com.netflix.kayenta.canaryanalysis.orca.task.RunCanaryTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class RunCanaryStage implements StageDefinitionBuilder, CancellableStage {

  private final ExecutionRepository executionRepository;

  @Autowired
  public RunCanaryStage(ExecutionRepository executionRepository) {
    this.executionRepository = executionRepository;
  }

  public static final String STAGE_TYPE = "runCanary";
  public static final String STAGE_NAME_PREFIX = "Run Canary #";

  @Override
  public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    builder
        .withTask("runCanary", RunCanaryTask.class)
        .withTask("monitorCanary", MonitorCanaryTask.class);
  }

  @Nonnull
  @Override
  public String getType() {
    return STAGE_TYPE;
  }

  @Override
  public Result cancel(Stage stage) {
    Map<String, Object> context = stage.getContext();
    String canaryPipelineExecutionId = (String) context.getOrDefault("canaryPipelineExecutionId", null);

    if (canaryPipelineExecutionId != null) {
      log.info("Cancelling stage (stageId: {}: executionId: {}, canaryPipelineExecutionId: {}, context: {})",
          stage.getId(), stage.getExecution().getId(), canaryPipelineExecutionId, stage.getContext());

      try {
        log.info("Cancelling pipeline execution {}...", canaryPipelineExecutionId);

        Execution pipeline = executionRepository.retrieve(Execution.ExecutionType.PIPELINE, canaryPipelineExecutionId);

        if (pipeline.getStatus().isComplete()) {
          log.debug("Not changing status of pipeline execution {} to CANCELED since execution is already completed: {}", canaryPipelineExecutionId, pipeline.getStatus());
          return new CancellableStage.Result(stage, new HashMap<>());
        }

        executionRepository.cancel(Execution.ExecutionType.PIPELINE, canaryPipelineExecutionId);
        executionRepository.updateStatus(Execution.ExecutionType.PIPELINE, canaryPipelineExecutionId, ExecutionStatus.CANCELED);
      } catch (Exception e) {
        log.error("Failed to cancel stage (stageId: {}, executionId: {}), e: {}",
            stage.getId(), stage.getExecution().getId(), e.getMessage(), e);
      }
    } else {
      log.info("Not cancelling stage (stageId: {}: executionId: {}, context: {}) since no canary pipeline execution id exists",
          stage.getId(), stage.getExecution().getId(), stage.getContext());
    }

    return new CancellableStage.Result(stage, new HashMap<>());
  }
}
