/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipelinetemplate.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner;
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.StageGraphBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode.Builder;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.pipeline.UpdatePipelineStage;
import com.netflix.spinnaker.orca.pipeline.StageExecutionFactory;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipelinetemplate.tasks.PlanTemplateDependentsTask;
import com.netflix.spinnaker.orca.pipelinetemplate.tasks.UpdatePipelineTemplateTask;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import java.util.*;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UpdatePipelineTemplateStage implements StageDefinitionBuilder {

  @Autowired(required = false)
  private Front50Service front50Service;

  @Autowired private ObjectMapper pipelineTemplateObjectMapper;

  @Autowired(required = false)
  private UpdatePipelineStage updatePipelineStage;

  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull Builder builder) {
    if (!Boolean.valueOf(
        stage.getContext().getOrDefault("skipPlanDependents", "false").toString())) {
      builder.withTask("planDependentPipelines", PlanTemplateDependentsTask.class);
    }

    builder.withTask("updatePipelineTemplate", UpdatePipelineTemplateTask.class);
  }

  @Override
  public void afterStages(@Nonnull StageExecution stage, @Nonnull StageGraphBuilder graph) {
    if (front50Service == null) {
      return;
    }

    if (!stage.getContext().containsKey("pipelineTemplate")) {
      throw new IllegalArgumentException("Missing required task parameter (pipelineTemplate)");
    }

    if (!(stage.getContext().get("pipelineTemplate") instanceof String)) {
      throw new IllegalArgumentException(
          "'pipelineTemplate' context key must be a base64-encoded string: Ensure you're on the most recent version of gate");
    }

    PipelineTemplate pipelineTemplate =
        ((StageExecutionImpl) stage)
            .decodeBase64(
                "/pipelineTemplate", PipelineTemplate.class, pipelineTemplateObjectMapper);

    List<Map<String, Object>> dependentPipelines =
        Retrofit2SyncCall.execute(
            front50Service.getPipelineTemplateDependents(pipelineTemplate.getId(), true));

    dependentPipelines.stream()
        .map(pipeline -> configureSavePipelineStage(stage, pipeline))
        .forEach(graph::append);
  }

  private StageExecution configureSavePipelineStage(
      StageExecution stage, Map<String, Object> pipeline) {
    Map<String, Object> context = new HashMap<>();

    try {
      context.put(
          "pipeline",
          Base64.getEncoder()
              .encodeToString(pipelineTemplateObjectMapper.writeValueAsBytes(pipeline)));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(
          String.format("Failed converting pipeline to JSON: %s", pipeline.get("id")), e);
    }

    return StageExecutionFactory.newStage(
        stage.getExecution(),
        updatePipelineStage.getType(),
        "updateDependentPipeline",
        context,
        stage,
        SyntheticStageOwner.STAGE_AFTER);
  }
}
