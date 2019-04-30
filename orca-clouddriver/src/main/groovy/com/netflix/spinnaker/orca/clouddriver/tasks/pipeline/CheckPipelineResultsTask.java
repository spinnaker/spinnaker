/*
 * Copyright 2019 Pivotal, Inc.
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
package com.netflix.spinnaker.orca.clouddriver.tasks.pipeline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class CheckPipelineResultsTask implements Task {

  private final ObjectMapper objectMapper;

  public CheckPipelineResultsTask(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public TaskResult execute(Stage stage) {
    final SavePipelineResultsData previousSavePipelineResults = stage.mapTo(SavePipelineResultsData.class);
    final SavePipelinesData savePipelinesData = stage.mapTo(SavePipelinesData.class);
    final List<PipelineReferenceData> previousCreated = previousSavePipelineResults.getPipelinesCreated();
    final List<PipelineReferenceData> previousUpdated = previousSavePipelineResults.getPipelinesUpdated();
    final List<PipelineReferenceData> previousFailedToSave = previousSavePipelineResults.getPipelinesFailedToSave();
    final SavePipelineResultsData savePipelineResults = new SavePipelineResultsData(
      previousCreated == null ? new ArrayList() : previousCreated,
      previousUpdated == null ? new ArrayList() : previousUpdated,
      previousFailedToSave == null ? new ArrayList() : previousFailedToSave
    );

    stage.getTasks().stream().filter( task -> task.getName().equals("savePipeline")).findFirst()
      .ifPresent(savePipelineTask -> {
        final String application = (String) stage.getContext().get("application");
        final String pipelineName = (String) stage.getContext().get("pipeline.name");
        final String pipelineId = (String) stage.getContext().get("pipeline.id");
        final PipelineReferenceData ref = new PipelineReferenceData(application, pipelineName, pipelineId);
        if (savePipelineTask.getStatus().isSuccessful()) {
          final Boolean isExistingPipeline = (Boolean) Optional.ofNullable(stage.getContext().get("isExistingPipeline"))
            .orElse(false);
          if (isExistingPipeline) {
            savePipelineResults.getPipelinesUpdated().add(ref);
          } else {
            savePipelineResults.getPipelinesCreated().add(ref);
          }
        } else {
          savePipelineResults.getPipelinesFailedToSave().add(ref);
        }
      });

    final Map<String, ?> output = objectMapper.
      convertValue(savePipelineResults, new TypeReference<Map<String, Object>>() {});
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(output).build();
  }

}
