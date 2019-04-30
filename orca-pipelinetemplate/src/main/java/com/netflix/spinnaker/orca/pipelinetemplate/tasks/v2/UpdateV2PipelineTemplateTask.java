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

package com.netflix.spinnaker.orca.pipelinetemplate.tasks.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate;
import org.apache.commons.codec.binary.Base64;
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
public class UpdateV2PipelineTemplateTask implements RetryableTask, SaveV2PipelineTemplateTask {

  @Autowired(required = false)
  private Front50Service front50Service;

  @Autowired
  private ObjectMapper pipelineTemplateObjectMapper;

  @SuppressWarnings("unchecked")
  @Override
  public TaskResult execute(Stage stage) {
    if (front50Service == null) {
      throw new UnsupportedOperationException("Front50 is not enabled, no way to save pipeline templates. Fix this by setting front50.enabled: true");
    }

    if (!stage.getContext().containsKey("pipelineTemplate")) {
      throw new IllegalArgumentException("Missing required task parameter (pipelineTemplate)");
    }

    if (!(stage.getContext().get("pipelineTemplate") instanceof String) ||
      !Base64.isBase64((String) stage.getContext().get("pipelineTemplate"))) {
      throw new IllegalArgumentException("'pipelineTemplate' context key must be a base64-encoded string: Ensure you're on the most recent version of gate");
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

    V2PipelineTemplate pipelineTemplate = stage.decodeBase64(
      "/pipelineTemplate",
      V2PipelineTemplate.class,
      pipelineTemplateObjectMapper
    );

    validate(pipelineTemplate);

    String tag = (String) stage.getContext().get("tag");
    Response response = front50Service.updateV2PipelineTemplate((String) stage.getContext().get("id"),
      tag, (Map<String, Object>) stage.decodeBase64("/pipelineTemplate", Map.class, pipelineTemplateObjectMapper));

    // TODO(jacobkiefer): Reduce duplicated code.
    String templateId = StringUtils.isEmpty(tag) ? pipelineTemplate.getId() : String.format("%s:%s", pipelineTemplate.getId(), tag);
    Map<String, Object> outputs = new HashMap<>();
    outputs.put("notification.type", "updatepipelinetemplate");
    outputs.put("pipelineTemplate.id", templateId);

    if (response.getStatus() == HttpStatus.OK.value()) {
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build();
    }

    return TaskResult.builder(ExecutionStatus.TERMINAL).context(outputs).build();
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
