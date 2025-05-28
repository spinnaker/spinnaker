/*
 * Copyright 2019 Google, Inc.
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
package com.netflix.spinnaker.orca.front50.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.front50.Front50Service;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import retrofit2.Response;

@Component
public class ReorderPipelinesTask implements Task {
  @Autowired(required = false)
  private Front50Service front50Service;

  @Autowired ObjectMapper objectMapper;

  @Nonnull
  @SuppressWarnings("unchecked")
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    validateTask(stage);

    Map<String, Integer> idsToIndices;
    try {
      idsToIndices = (Map<String, Integer>) stage.decodeBase64("/idsToIndices", Map.class);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "`idsToIndices` context key must be a base64-encoded string: Ensure you're on the most recent version of gate",
          e);
    }

    String application;
    try {
      application = stage.decodeBase64("/application", String.class);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "`application` context key must be a base64-encoded string: Ensure you're on the most recent version of gate",
          e);
    }

    Boolean isStrategy;
    try {
      isStrategy = stage.decodeBase64("/isStrategy", Boolean.class);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "`isStrategy` context key must be a base64-encoded string: Ensure you're on the most recent version of gate",
          e);
    }

    Front50Service.ReorderPipelinesCommand reorderPipelinesCommand =
        new Front50Service.ReorderPipelinesCommand(idsToIndices, application);

    Response<ResponseBody> response =
        isStrategy
            ? Retrofit2SyncCall.executeCall(
                front50Service.reorderPipelineStrategies(reorderPipelinesCommand))
            : Retrofit2SyncCall.executeCall(
                front50Service.reorderPipelines(reorderPipelinesCommand));

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("notification.type", "reorderpipelines");
    outputs.put("application", application);

    return TaskResult.builder(
            (response.code() == HttpStatus.OK.value())
                ? ExecutionStatus.SUCCEEDED
                : ExecutionStatus.TERMINAL)
        .context(outputs)
        .build();
  }

  private void validateTask(StageExecution stage) {
    if (front50Service == null) {
      throw new UnsupportedOperationException(
          "Front50 is not enabled, no way to reorder pipeline. Fix this by setting front50.enabled: true");
    }

    if (!stage.getContext().containsKey("idsToIndices")) {
      throw new IllegalArgumentException("`idsToIndices` context key must be provided");
    }

    if (!(stage.getContext().get("idsToIndices") instanceof String)) {
      throw new IllegalArgumentException(
          "`idsToIndices` context key must be a base64-encoded string: Ensure you're on the most recent version of gate");
    }

    if (!stage.getContext().containsKey("application")) {
      throw new IllegalArgumentException("`application` context key must be provided");
    }

    if (!(stage.getContext().get("application") instanceof String)) {
      throw new IllegalArgumentException(
          "`application` context key must be a base64-encoded string: Ensure you're on the most recent version of gate");
    }

    if (!stage.getContext().containsKey("isStrategy")) {
      throw new IllegalArgumentException("`isStrategy` context key must be provided");
    }

    if (!(stage.getContext().get("isStrategy") instanceof String)) {
      throw new IllegalArgumentException(
          "`isStrategy` context key must be a base64-encoded string: Ensure you're on the most recent version of gate");
    }
  }
}
