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
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import retrofit.client.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class UpdatePipelineTemplateTask implements RetryableTask, SavePipelineTemplateTask {

  @Autowired(required = false)
  private Front50Service front50Service;

  @Autowired
  private ObjectMapper pipelineTemplateObjectMapper;

  @SuppressWarnings("unchecked")
  @Override
  public TaskResult execute(Stage stage) {
    if (front50Service == null) {
      throw new UnsupportedOperationException("Front50 is not enabled, no way to fetch pager duty. Fix this by setting front50.enabled: true");
    }

    List<String> missingParams = new ArrayList<>();
    if (!stage.getContext().containsKey("id")) {
      missingParams.add("id");
    }

    if (!stage.getContext().containsKey("pipelineTemplate")) {
      missingParams.add("pipelineTemplate");
    }

    if (!missingParams.isEmpty()) {
      throw new IllegalArgumentException("Missing required task parameter (" +
        StringUtils.arrayToCommaDelimitedString(missingParams.toArray()) +
        ")");
    }

    PipelineTemplate pipelineTemplate;
    try {
      pipelineTemplate = pipelineTemplateObjectMapper.convertValue(stage.getContext().get("pipelineTemplate"), PipelineTemplate.class);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Pipeline template task parameter is not valid", e);
    }

    validate(pipelineTemplate);

    Response response = front50Service.updatePipelineTemplate(
      (String) stage.getContext().get("id"),
      (Map<String, Object>) stage.getContext().get("pipelineTemplate")
    );

    // TODO rz - app & account context?
    Map<String, Object> outputs = new HashMap<>();
    outputs.put("notification.type", "updatepipelinetemplate");
    outputs.put("pipelineTemplate.id", pipelineTemplate.getId());

    if (response.getStatus() == HttpStatus.OK.value()) {
      return new TaskResult(ExecutionStatus.SUCCEEDED, outputs);
    }

    return new TaskResult(ExecutionStatus.TERMINAL, outputs);
  }

  @Override
  public long getBackoffPeriod() {
    return 15000;
  }

  @Override
  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(1);
  }
}
