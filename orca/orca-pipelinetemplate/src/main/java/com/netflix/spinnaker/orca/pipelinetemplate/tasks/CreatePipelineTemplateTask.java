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
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import retrofit2.Response;

@Component
public class CreatePipelineTemplateTask implements RetryableTask, SavePipelineTemplateTask {

  @Autowired(required = false)
  private Front50Service front50Service;

  @Autowired private ObjectMapper pipelineTemplateObjectMapper;

  @SuppressWarnings("unchecked")
  @Override
  public TaskResult execute(StageExecution stage) {
    if (front50Service == null) {
      throw new UnsupportedOperationException(
          "Front50 is not enabled, no way to save pipeline templates. Fix this by setting front50.enabled: true");
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

    validate(pipelineTemplate);

    Response<ResponseBody> response =
        Retrofit2SyncCall.executeCall(
            front50Service.savePipelineTemplate(
                (Map<String, Object>)
                    ((StageExecutionImpl) stage)
                        .decodeBase64(
                            "/pipelineTemplate", Map.class, pipelineTemplateObjectMapper)));

    // TODO rz - app & account context?
    Map<String, Object> outputs = new HashMap<>();
    outputs.put("notification.type", "createpipelinetemplate");
    outputs.put("pipelineTemplate.id", pipelineTemplate.getId());

    if (response.code() == HttpStatus.OK.value()) {
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
