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

import com.netflix.spinnaker.orca.pipelinetemplate.TemplatedPipelineRequest;
import com.netflix.spinnaker.orca.pipelinetemplate.generator.ExecutionGenerator;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate.Configuration;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;

import java.util.*;
import java.util.stream.Collectors;

public class V1SchemaExecutionGenerator implements ExecutionGenerator {

  @Override
  public Map<String, Object> generate(PipelineTemplate template, TemplateConfiguration configuration, TemplatedPipelineRequest request) {
    Map<String, Object> pipeline = new HashMap<>();
    pipeline.put("id", Optional.ofNullable(request.getId()).orElse(Optional.ofNullable(configuration.getPipeline().getPipelineConfigId()).orElse("unknown")));
    pipeline.put("application", configuration.getPipeline().getApplication());
    if (template.getSource() != null) {
      Map<String, String> source = new HashMap<>();
      source.put("id", template.getSource());
      source.put("type", "templatedPipeline");
      pipeline.put("source", source);
    }
    if (request.getExecutionId() != null) {
      pipeline.put("executionId", request.getExecutionId());
    }
    pipeline.put("name", Optional.ofNullable(configuration.getPipeline().getName()).orElse("Unnamed Execution"));

    Configuration c = template.getConfiguration();
    if (c.getConcurrentExecutions().isEmpty()) {
      pipeline.put("limitConcurrent", request.isLimitConcurrent());
      pipeline.put("keepWaitingPipelines", request.isKeepWaitingPipelines());
    } else {
      pipeline.put("limitConcurrent", c.getConcurrentExecutions().getOrDefault("limitConcurrent", request.isLimitConcurrent()));
      pipeline.put("keepWaitingPipelines", c.getConcurrentExecutions().getOrDefault("keepWaitingPipelines", request.isKeepWaitingPipelines()));
    }

    addNotifications(pipeline, template, configuration);
    addParameters(pipeline, template, configuration);
    addTriggers(pipeline, template, configuration);

    pipeline.put("stages", template.getStages()
      .stream()
      .map(s -> {
        Map<String, Object> stage = new HashMap<>();
        stage.put("id", UUID.randomUUID().toString());
        stage.put("refId", s.getId());
        stage.put("type", s.getType());
        stage.put("name", s.getName());
        stage.put("requisiteStageRefIds", s.getRequisiteStageRefIds());
        if (s.getPartialDefinitionContext() != null) {
          stage.put("group", String.format("%s: %s",
            s.getPartialDefinitionContext().getPartialDefinition().getName(),
            s.getPartialDefinitionContext().getMarkerStage().getName()
          ));
        }
        stage.putAll(s.getConfigAsMap());
        return stage;
      })
      .collect(Collectors.toList()));
    if (request.getTrigger() != null && !request.getTrigger().isEmpty()) {
      pipeline.put("trigger", request.getTrigger());
    }

    return pipeline;
  }

  private void addNotifications(Map<String, Object> pipeline, PipelineTemplate template, TemplateConfiguration configuration) {
    if (configuration.getConfiguration().getInherit().contains("notifications")) {
      pipeline.put(
        "notifications",
        TemplateMerge.mergeNamedContent(
          template.getConfiguration().getNotifications(),
          configuration.getConfiguration().getNotifications()
        )
      );
    } else {
      pipeline.put(
        "notifications",
        Optional.ofNullable(configuration.getConfiguration().getNotifications()).orElse(Collections.emptyList())
      );
    }
  }

  private void addParameters(Map<String, Object> pipeline, PipelineTemplate template, TemplateConfiguration configuration) {
    if (configuration.getConfiguration().getInherit().contains("parameters")) {
      pipeline.put(
        "parameterConfig",
        TemplateMerge.mergeNamedContent(
          template.getConfiguration().getParameters(),
          configuration.getConfiguration().getParameters()
        )
      );
    } else {
      pipeline.put(
        "parameterConfig",
        Optional.ofNullable(configuration.getConfiguration().getParameters()).orElse(Collections.emptyList())
      );
    }
  }

  private void addTriggers(Map<String, Object> pipeline,
                           PipelineTemplate template,
                           TemplateConfiguration configuration) {
    if (configuration.getConfiguration().getInherit().contains("triggers")) {
      pipeline.put(
        "triggers",
        TemplateMerge.mergeNamedContent(
          template.getConfiguration().getTriggers(),
          configuration.getConfiguration().getTriggers()
        )
      );
    } else {
      pipeline.put(
        "triggers",
        Optional.ofNullable(configuration.getConfiguration().getTriggers()).orElse(Collections.emptyList())
      );
    }
  }
}
