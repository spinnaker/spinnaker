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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class PreparePipelineToSaveTask implements Task {

  private Logger log = LoggerFactory.getLogger(getClass());

  private final ObjectMapper objectMapper;

  public PreparePipelineToSaveTask(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public TaskResult execute(Stage stage) {
    final SavePipelinesData input = stage.mapTo(SavePipelinesData.class);
    if (input.getPipelinesToSave() == null || input.getPipelinesToSave().isEmpty()) {
      log.info("There are no pipelines to save.");
      return TaskResult.ofStatus(ExecutionStatus.TERMINAL);
    }
    final Map pipelineData = input.getPipelinesToSave().get(0);
    final String pipelineString;
    try {
      pipelineString = objectMapper.writeValueAsString(pipelineData);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
    final String encodedPipeline = Base64.getEncoder().encodeToString(pipelineString.getBytes());
    final List<Map> remainingPipelinesToSave = input.getPipelinesToSave().subList(1, input.getPipelinesToSave().size());
    final SavePipelinesData outputSavePipelinesData = new SavePipelinesData(encodedPipeline, remainingPipelinesToSave);
    final Map output = objectMapper.convertValue(outputSavePipelinesData, new TypeReference<Map<String, Object>>() {});
    output.put("isExistingPipeline", pipelineData.get("id") != null);
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(output).build();
  }

}
