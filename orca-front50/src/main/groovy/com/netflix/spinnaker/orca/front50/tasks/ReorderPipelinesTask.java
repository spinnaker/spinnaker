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
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

import java.util.HashMap;
import java.util.Map;

@Component
public class ReorderPipelinesTask implements Task {
  @Autowired(required = false)
  private Front50Service front50Service;

  @Autowired
  ObjectMapper objectMapper;

  @SuppressWarnings("unchecked")
  @Override
  public TaskResult execute(Stage stage) {
    validateTask(stage);

    Map<String, Integer> idsToIndices;
    try {
      idsToIndices = (Map<String, Integer>) stage.decodeBase64("/idsToIndices", Map.class);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("`idsToIndices` context key must be a base64-encoded string: Ensure you're on the most recent version of gate", e);
    }

    String application;
    try {
      application = stage.decodeBase64("/application", String.class);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("`application` context key must be a base64-encoded string: Ensure you're on the most recent version of gate", e);
    }

    Boolean isStrategy;
    try {
      isStrategy = stage.decodeBase64("/isStrategy", Boolean.class);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("`isStrategy` context key must be a base64-encoded string: Ensure you're on the most recent version of gate", e);
    }

    Front50Service.ReorderPipelinesCommand reorderPipelinesCommand = new Front50Service.ReorderPipelinesCommand(idsToIndices, application);

    Response response = isStrategy ?
      front50Service.reorderPipelineStrategies(reorderPipelinesCommand) :
      front50Service.reorderPipelines(reorderPipelinesCommand);

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("notification.type", "reorderpipelines");
    outputs.put("application", application);

    return new TaskResult(
      (response.getStatus() == HttpStatus.OK.value()) ? ExecutionStatus.SUCCEEDED : ExecutionStatus.TERMINAL,
      outputs
    );
  }

  private void validateTask(Stage stage) {
    if (front50Service == null) {
      throw new UnsupportedOperationException("Front50 is not enabled, no way to reorder pipeline. Fix this by setting front50.enabled: true");
    }

    if (!stage.getContext().containsKey("idsToIndices")) {
      throw new IllegalArgumentException("`idsToIndices` context key must be provided");
    }

    if (!(stage.getContext().get("idsToIndices") instanceof String)) {
      throw new IllegalArgumentException("`idsToIndices` context key must be a base64-encoded string: Ensure you're on the most recent version of gate");
    }

    if (!stage.getContext().containsKey("application")) {
      throw new IllegalArgumentException("`application` context key must be provided");
    }

    if (!(stage.getContext().get("application") instanceof String)) {
      throw new IllegalArgumentException("`application` context key must be a base64-encoded string: Ensure you're on the most recent version of gate");
    }

    if (!stage.getContext().containsKey("isStrategy")) {
      throw new IllegalArgumentException("`isStrategy` context key must be provided");
    }

    if (!(stage.getContext().get("isStrategy") instanceof String)) {
      throw new IllegalArgumentException("`isStrategy` context key must be a base64-encoded string: Ensure you're on the most recent version of gate");
    }
  }
}
