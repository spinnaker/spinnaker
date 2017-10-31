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
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.pipeline.UpdatePipelineStage;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode.Builder;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner;
import com.netflix.spinnaker.orca.pipelinetemplate.tasks.PlanTemplateDependentsTask;
import com.netflix.spinnaker.orca.pipelinetemplate.tasks.UpdatePipelineTemplateTask;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class UpdatePipelineTemplateStage implements StageDefinitionBuilder {

  @Autowired(required = false)
  private Front50Service front50Service;

  @Autowired
  private ObjectMapper pipelineTemplateObjectMapper;

  @Autowired(required = false)
  private UpdatePipelineStage updatePipelineStage;

  @Override
  public <T extends Execution<T>> void taskGraph(Stage<T> stage, Builder builder) {
    if (!Boolean.valueOf(stage.getContext().getOrDefault("skipPlanDependents", "false").toString())) {
      builder.withTask("planDependentPipelines", PlanTemplateDependentsTask.class);
    }

    builder
      .withTask("updatePipelineTemplate", UpdatePipelineTemplateTask.class);
  }

  @Nonnull
  @Override
  public <T extends Execution<T>> List<Stage<T>> aroundStages(@Nonnull Stage<T> stage) {
    if (front50Service == null) {
      return Collections.emptyList();
    }

    if (!stage.getContext().containsKey("pipelineTemplate")) {
      throw new IllegalArgumentException("Missing required task parameter (pipelineTemplate)");
    }

    if (!(stage.getContext().get("pipelineTemplate") instanceof String)) {
      throw new IllegalArgumentException("'pipelineTemplate' context key must be a base64-encoded string: Ensure you're on the most recent version of gate");
    }

    PipelineTemplate pipelineTemplate = stage.decodeBase64(
      "/pipelineTemplate",
      PipelineTemplate.class,
      pipelineTemplateObjectMapper
    );

    List<Map<String, Object>> dependentPipelines = front50Service.getPipelineTemplateDependents(pipelineTemplate.getId(), true);

    return dependentPipelines.stream()
      .filter(pipeline -> {
        // We only need to re-save pipelines that actually inherit configurations.
        TemplateConfiguration config = pipelineTemplateObjectMapper.convertValue(pipeline.get("config"), TemplateConfiguration.class);
        return !config.getConfiguration().getInherit().isEmpty();
      })
      .map(pipeline -> configureSavePipelineStage(stage, pipeline))
      .collect(Collectors.toList());
  }

  private <T extends Execution<T>> Stage<T> configureSavePipelineStage(Stage<T> stage, Map<String, Object> pipeline) {
    Map<String, Object> context = new HashMap<>();

    try {
      context.put("pipeline", Base64.getEncoder().encodeToString(pipelineTemplateObjectMapper.writeValueAsBytes(pipeline)));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(String.format("Failed converting pipeline to JSON: %s", pipeline.get("id")), e);
    }

    return StageDefinitionBuilder.newStage(
      stage.getExecution(),
      updatePipelineStage.getType(),
      "updateDependentPipeline",
      context,
      stage,
      SyntheticStageOwner.STAGE_AFTER
    );
  }
}
