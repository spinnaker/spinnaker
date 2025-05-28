/*
 * Copyright 2017 Google, Inc.
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

import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.front50.Front50Service;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import retrofit2.Response;

@Component
public class DeletePipelineTemplateTask implements RetryableTask {
  @Autowired(required = false)
  private Front50Service front50Service;

  @SuppressWarnings("unchecked")
  @Override
  public TaskResult execute(StageExecution stage) {
    if (front50Service == null) {
      throw new UnsupportedOperationException(
          "Front50 is not enabled, no way to delete pipeline. Fix this by setting front50.enabled: true");
    }

    if (!stage.getContext().containsKey("pipelineTemplateId")) {
      throw new IllegalArgumentException("Missing required task parameter (pipelineTemplateId)");
    }

    String templateId = (String) stage.getContext().get("pipelineTemplateId");

    Response<ResponseBody> response =
        Retrofit2SyncCall.executeCall(front50Service.deletePipelineTemplate(templateId));

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("notification.type", "deletepipelinetemplate");
    outputs.put("pipeline.id", templateId);

    return TaskResult.builder(
            (response.code() == HttpStatus.OK.value())
                ? ExecutionStatus.SUCCEEDED
                : ExecutionStatus.TERMINAL)
        .context(outputs)
        .build();
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
