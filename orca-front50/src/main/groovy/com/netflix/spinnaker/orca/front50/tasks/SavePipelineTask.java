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
package com.netflix.spinnaker.orca.front50.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.PipelineModelMutator;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class SavePipelineTask implements RetryableTask {

  private Logger log = LoggerFactory.getLogger(getClass());

  @Autowired(required = false)
  private Front50Service front50Service;

  @Autowired(required = false)
  private List<PipelineModelMutator> pipelineModelMutators = new ArrayList<>();

  @Autowired
  ObjectMapper objectMapper;

  @SuppressWarnings("unchecked")
  @Override
  public TaskResult execute(Stage stage) {
    if (front50Service == null) {
      throw new UnsupportedOperationException("Front50 is not enabled, no way to save pipeline. Fix this by setting front50.enabled: true");
    }

    if (!stage.getContext().containsKey("pipeline")) {
      throw new IllegalArgumentException("pipeline context must be provided");
    }

    if (!(stage.getContext().get("pipeline") instanceof String)) {
      throw new IllegalArgumentException("'pipeline' context key must be a base64-encoded string: Ensure you're on the most recent version of gate");
    }

    byte[] pipelineData;
    try {
      pipelineData = Base64.getDecoder().decode((String) stage.getContext().get("pipeline"));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("pipeline must be encoded as base64", e);
    }
    log.info("Expanded encoded pipeline:" + new String(pipelineData));

    Map<String, Object> pipeline = (Map<String, Object>) stage.decodeBase64("/pipeline", Map.class);

    if (!pipeline.containsKey("index")) {
      Map<String, Object> existingPipeline = fetchExistingPipeline(pipeline);
      if (existingPipeline != null) {
        pipeline.put("index", existingPipeline.get("index"));
      }
    }
    String serviceAccount = (String) stage.getContext().get("pipeline.serviceAccount");
    if (serviceAccount != null) {
      updateServiceAccount(pipeline, serviceAccount);
    }

    pipelineModelMutators.stream().filter(m -> m.supports(pipeline)).forEach(m -> m.mutate(pipeline));

    Response response = front50Service.savePipeline(pipeline);

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("notification.type", "savepipeline");
    outputs.put("application", pipeline.get("application"));
    outputs.put("pipeline.name", pipeline.get("name"));

    try {
      Map<String, Object> savedPipeline = (Map<String, Object>) objectMapper.readValue(
        response.getBody().in(), Map.class
      );
      outputs.put("pipeline.id", savedPipeline.get("id"));
    } catch (Exception e) {
      log.error("Unable to deserialize saved pipeline, reason: ", e.getMessage());

      if (pipeline.containsKey("id")) {
        outputs.put("pipeline.id", pipeline.get("id"));
      }
    }

    return new TaskResult(
      (response.getStatus() == HttpStatus.OK.value()) ? ExecutionStatus.SUCCEEDED : ExecutionStatus.TERMINAL,
      outputs
    );
  }

  @Override
  public long getBackoffPeriod() {
    return 1000;
  }

  @Override
  public long getTimeout() {
    return TimeUnit.SECONDS.toMillis(30);
  }

  private void updateServiceAccount(Map<String, Object> pipeline, String serviceAccount) {
    if (StringUtils.isEmpty(serviceAccount) || !pipeline.containsKey("triggers")) {
      return;
    }

    List<Map<String, Object>> triggers = (List<Map<String, Object>>) pipeline.get("triggers");
    List<String> roles = (List<String>) pipeline.get("roles");
    // Managed service acct but no roles; Remove runAsUserFrom triggers
    if (roles == null || roles.isEmpty()) {
      triggers.forEach(t -> t.remove("runAsUser", serviceAccount));
      return;
    }

    // Managed Service account exists and roles are set; Update triggers
    triggers.forEach(t -> t.putIfAbsent("runAsUser", serviceAccount));
  }

  private Map<String, Object> fetchExistingPipeline(Map<String, Object> newPipeline) {
    String applicationName = (String) newPipeline.get("application");
    String newPipelineID = (String) newPipeline.get("id");
    if (!StringUtils.isEmpty(newPipelineID)) {
      return front50Service.getPipelines(applicationName).stream()
        .filter(m -> m.containsKey("id"))
        .filter(m -> m.get("id").equals(newPipelineID))
        .findFirst()
        .orElse(null);
    }
    return null;
  }
}
