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
package com.netflix.spinnaker.orca.pipelinetemplate.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.extensionpoint.pipeline.PipelinePreprocessor;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class PlanTemplateDependentsTask implements RetryableTask {

  @Autowired(required = false)
  private Front50Service front50Service;

  @Autowired
  private ObjectMapper pipelineTemplateObjectMapper;

  @Autowired
  private PipelinePreprocessor pipelineTemplatePreprocessor;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    if (front50Service == null) {
      throw new UnsupportedOperationException("Front50 is not enabled. Fix this by setting front50.enabled: true");
    }

    if (!stage.getContext().containsKey("pipelineTemplate")) {
      throw new IllegalArgumentException("Missing required task parameter (pipelineTemplate)");
    }

    if (!(stage.getContext().get("pipelineTemplate") instanceof String)) {
      throw new IllegalArgumentException("'pipelineTemplate' context key must be a base64-encoded string: Ensure you're on the most recent version of gate");
    }

    PipelineTemplate pipelineTemplate = (PipelineTemplate) stage.decodeBase64(
      "/pipelineTemplate",
      PipelineTemplate.class,
      pipelineTemplateObjectMapper
    );

    List<Map<String, Object>> dependentPipelines = front50Service.getPipelineTemplateDependents(pipelineTemplate.getId(), false);

    Map<String, Object> errorResponses = new HashMap<>();
    for (Map<String, Object> dependentPipeline : dependentPipelines) {
      Map<String, Object> request = new HashMap<>();
      request.put("type", "templatedPipeline");
      request.put("trigger", new HashMap<>());
      request.put("config", dependentPipeline.get("config"));
      request.put("template", pipelineTemplate);
      request.put("plan", true);

      Map<String, Object> response = pipelineTemplatePreprocessor.process(request);
      if (response.containsKey("errors")) {
        errorResponses.put((String) dependentPipeline.get("id"), response.get("errors"));
      }
    }

    Map<String, Object> context = new HashMap<>();
    context.put("notification.type", "plantemplatedependents");
    context.put("pipelineTemplate.id", pipelineTemplate.getId());
    context.put("pipelineTemplate.allDependentPipelines", dependentPipelines.stream().map(it -> it.get("id")).collect(Collectors.toList()));

    if (!errorResponses.isEmpty()) {
      context.put("pipelineTemplate.dependentErrors", errorResponses);
    }

    return new TaskResult(
      errorResponses.isEmpty() ? ExecutionStatus.SUCCEEDED : ExecutionStatus.TERMINAL,
      context,
      Collections.emptyMap()
    );
  }

  @Override
  public long getBackoffPeriod() {
    return 15000;
  }

  @Override
  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(10);
  }
}
