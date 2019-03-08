/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.orca.pipelinetemplate.v2schema;

import com.netflix.spinnaker.orca.pipelinetemplate.TemplatedPipelineRequest;
import com.netflix.spinnaker.orca.pipelinetemplate.generator.V2ExecutionGenerator;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.TemplateMerge;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2TemplateConfiguration;

import java.util.*;

public class V2SchemaExecutionGenerator implements V2ExecutionGenerator {

  @Override
  public Map<String, Object> generate(V2PipelineTemplate template, V2TemplateConfiguration configuration, TemplatedPipelineRequest request) {
    Map<String, Object> pipeline = template.getPipeline();
    pipeline.put("id", Optional.ofNullable(request.getId()).orElse(Optional.ofNullable(configuration.getPipelineConfigId()).orElse("unknown")));
    pipeline.put("application", configuration.getApplication());
    if (request.getExecutionId() != null) {
      pipeline.put("executionId", request.getExecutionId());
    }
    pipeline.put("name", Optional.ofNullable(configuration.getName()).orElse("Unnamed Execution"));

    if (!pipeline.containsKey("limitConcurrent")) {
      pipeline.put("limitConcurrent", request.isLimitConcurrent());
    }
    if (!pipeline.containsKey("keepWaitingPipelines")) {
      pipeline.put("keepWaitingPipelines", request.isKeepWaitingPipelines());
    }

    addNotifications(pipeline, template, configuration);
    addParameters(pipeline, template, configuration);
    addTriggers(pipeline, template, configuration);
    pipeline.put("templateVariables", configuration.getVariables());

    if (request.getTrigger() != null && !request.getTrigger().isEmpty()) {
      pipeline.put("trigger", request.getTrigger());
    }

    return pipeline;
  }

  private void addNotifications(Map<String, Object> pipeline, V2PipelineTemplate template, V2TemplateConfiguration configuration) {
    if (configuration.getInherit().contains("notifications")) {
      pipeline.put(
        "notifications",
        TemplateMerge.mergeDistinct(
          (List<HashMap<String, Object>>) template.getPipeline().get("notifications"),
          configuration.getNotifications()
        )
      );
    } else {
      pipeline.put(
        "notifications",
        Optional.ofNullable(configuration.getNotifications()).orElse(Collections.emptyList())
      );
    }
  }

  private void addParameters(Map<String, Object> pipeline, V2PipelineTemplate template, V2TemplateConfiguration configuration) {
    if (configuration.getInherit().contains("parameters")) {
      pipeline.put(
        "parameterConfig",
        TemplateMerge.mergeDistinct(
          (List<HashMap<String, Object>>) template.getPipeline().get("parameterConfig"),
          configuration.getParameters()
        )
      );
    } else {
      pipeline.put(
        "parameterConfig",
        Optional.ofNullable(configuration.getParameters()).orElse(Collections.emptyList())
      );
    }
  }

  private void addTriggers(Map<String, Object> pipeline,
                           V2PipelineTemplate template,
                           V2TemplateConfiguration configuration) {
    if (configuration.getInherit().contains("triggers")) {
      pipeline.put(
        "triggers",
        TemplateMerge.mergeDistinct(
          (List<HashMap<String, Object>>) template.getPipeline().get("triggers"),
          configuration.getTriggers()
        )
      );
    } else {
      pipeline.put(
        "triggers",
        Optional.ofNullable(configuration.getTriggers()).orElse(Collections.emptyList())
      );
    }
  }
}
