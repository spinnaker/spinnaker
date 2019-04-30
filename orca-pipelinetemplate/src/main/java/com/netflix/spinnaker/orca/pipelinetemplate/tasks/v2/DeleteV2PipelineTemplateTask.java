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

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class DeleteV2PipelineTemplateTask implements RetryableTask {
  @Autowired(required = false)
  private Front50Service front50Service;

  @SuppressWarnings("unchecked")
  @Override
  public TaskResult execute(Stage stage) {
    if (front50Service == null) {
      throw new UnsupportedOperationException("Front50 is not enabled, no way to delete pipeline. Fix this by setting front50.enabled: true");
    }

    if (!stage.getContext().containsKey("pipelineTemplateId")) {
      throw new IllegalArgumentException("Missing required task parameter (pipelineTemplateId)");
    }

    String templateId = (String) stage.getContext().get("pipelineTemplateId");
    String tag = (String) stage.getContext().get("tag");
    String digest = (String) stage.getContext().get("digest");

    Response _ = front50Service.deleteV2PipelineTemplate(templateId, tag, digest);

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("notification.type", "deletepipelinetemplate");
    outputs.put("pipeline.id", templateId);

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build();
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
