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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema;

import com.netflix.spinnaker.orca.pipelinetemplate.generator.ExecutionGenerator;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate.Configuration;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class V1SchemaExecutionGenerator implements ExecutionGenerator {

  @Override
  public Map<String, Object> generate(PipelineTemplate template, TemplateConfiguration configuration, String id) {
    Map<String, Object> pipeline = new HashMap<>();
    pipeline.put("id", Optional.ofNullable(configuration.getPipeline().getPipelineConfigId()).orElse(Optional.ofNullable(id).orElse(configuration.getRuntimeId())));
    pipeline.put("application", configuration.getPipeline().getApplication());
    pipeline.put("name", Optional.ofNullable(configuration.getPipeline().getName()).orElse("Unnamed Execution"));

    if (configuration.getPipeline().getExecutionEngine() != null) {
      pipeline.put("executionEngine", configuration.getPipeline().getExecutionEngine());
    }

    // TODO rz - Ehhhh
    Configuration c = template.getConfiguration();
    if (template.getConfiguration() == null) {
      pipeline.put("parallel", true);
      pipeline.put("limitConcurrent", true);
      pipeline.put("keepWaitingPipelines", false);
      pipeline.put("notifications", Collections.emptyList());
    } else {
      pipeline.put("parallel", c.getConcurrentExecutions().getOrDefault("parallel", true));
      pipeline.put("limitConcurrent", c.getConcurrentExecutions().getOrDefault("limitConcurrent", true));
      pipeline.put("keepWaitingPipelines", c.getConcurrentExecutions().getOrDefault("keepWaitingPipelines", false));
      pipeline.put("notifications", c.getNotifications());
    }

    pipeline.put("stages", template.getStages()
      .stream()
      .map(s -> {
        Map<String, Object> stage = new HashMap<>();
        stage.put("id", UUID.randomUUID().toString());
        stage.put("refId", s.getId());
        stage.put("type", s.getType());
        stage.put("name", s.getName());
        stage.put("requisiteStageRefIds", s.getRequisiteStageRefIds());
        stage.putAll(s.getConfig());
        return stage;
      })
      .collect(Collectors.toList()));

    return pipeline;
  }
}
