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

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.PipelineModelMutator;
import com.netflix.spinnaker.orca.front50.pipeline.SavePipelineStage;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

@Component
public class SavePipelineTask implements RetryableTask {

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Autowired
  SavePipelineTask(
      Optional<Front50Service> front50Service,
      Optional<List<PipelineModelMutator>> pipelineModelMutators,
      ObjectMapper objectMapper) {
    this.front50Service = front50Service.orElse(null);
    this.pipelineModelMutators = pipelineModelMutators.orElse(new ArrayList<>());
    this.objectMapper = objectMapper;
  }

  private final Front50Service front50Service;
  private final List<PipelineModelMutator> pipelineModelMutators;
  ObjectMapper objectMapper;

  @SuppressWarnings("unchecked")
  @Override
  public TaskResult execute(StageExecution stage) {
    if (front50Service == null) {
      throw new UnsupportedOperationException(
          "Front50 is not enabled, no way to save pipeline. Fix this by setting front50.enabled: true");
    }

    Map<String, Object> pipeline = new HashMap<>();
    List<Map<String, Object>> pipelines = new ArrayList<>();

    boolean isSavingMultiplePipelines =
        (boolean) stage.getContext().getOrDefault("isSavingMultiplePipelines", false);

    boolean isBulkSavingPipelines =
        (boolean) stage.getContext().getOrDefault("isBulkSavingPipelines", false);

    boolean staleCheck =
        (Boolean) Optional.ofNullable(stage.getContext().get("staleCheck")).orElse(false);

    if (isBulkSavingPipelines) {
      if (!stage.getContext().containsKey("pipelines")) {
        throw new IllegalArgumentException(
            "pipelines context must be provided when saving multiple pipelines");
      }
      pipelines = (List<Map<String, Object>>) stage.decodeBase64("/pipelines", List.class);
      log.info(
          "Bulk saving the following pipelines: {}",
          pipelines.stream().map(p -> p.get("name")).collect(Collectors.toList()));
    } else {
      if (!stage.getContext().containsKey("pipeline")) {
        throw new IllegalArgumentException(
            "pipeline context must be provided when saving a single pipeline");
      }
      if (!(stage.getContext().get("pipeline") instanceof String)) {
        pipeline = (Map<String, Object>) stage.getContext().get("pipeline");
      } else {
        pipeline = (Map<String, Object>) stage.decodeBase64("/pipeline", Map.class);
      }
      pipelines.add(pipeline);
      log.info("Saving single pipeline {}", pipeline.get("name"));
    }

    // Preprocess pipelines before saving
    for (Map<String, Object> pipe : pipelines) {
      if (!pipe.containsKey("index")) {
        Map<String, Object> existingPipeline = fetchExistingPipeline(pipe);
        if (existingPipeline != null) {
          pipe.put("index", existingPipeline.get("index"));
        }
      }

      String serviceAccount = (String) stage.getContext().get("pipeline.serviceAccount");
      if (serviceAccount != null) {
        updateServiceAccount(pipe, serviceAccount);
      }

      if (stage.getContext().get("pipeline.id") != null
          && pipe.get("id") == null
          && !isSavingMultiplePipelines) {
        pipe.put("id", stage.getContext().get("pipeline.id"));

        // We need to tell front50 to regenerate cron trigger id's
        pipe.put("regenerateCronTriggerIds", true);
      }

      pipelineModelMutators.stream().filter(m -> m.supports(pipe)).forEach(m -> m.mutate(pipe));
    }

    Response response;
    if (isBulkSavingPipelines) {
      response = front50Service.savePipelines(pipelines, staleCheck);
    } else {
      response = front50Service.savePipeline(pipeline, staleCheck);
    }

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("notification.type", "savepipeline");
    outputs.put("application", stage.getContext().get("application"));

    Map<String, Object> saveResult = new HashMap<>();
    try {
      saveResult = (Map<String, Object>) objectMapper.readValue(response.getBody().in(), Map.class);
    } catch (Exception e) {
      log.error("Unable to deserialize save pipeline(s) result, reason: ", e);
    }

    if (isBulkSavingPipelines) {
      outputs.put("bulksave", saveResult);
    } else {
      outputs.put("pipeline.name", pipeline.get("name"));
      outputs.put("pipeline.id", saveResult.getOrDefault("id", pipeline.getOrDefault("id", "")));
    }

    final ExecutionStatus status;
    if (response.getStatus() == HttpStatus.OK.value()) {
      status = ExecutionStatus.SUCCEEDED;
    } else {
      if (isSavingMultiplePipelines) {
        status = ExecutionStatus.FAILED_CONTINUE;
      } else {
        status = ExecutionStatus.TERMINAL;
      }
    }
    return TaskResult.builder(status).context(outputs).build();
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
    triggers.stream()
        .filter(t -> runAsUserIsNullOrManagedServiceAccount((String) t.get("runAsUser")))
        .forEach(t -> t.put("runAsUser", serviceAccount));
  }

  private Map<String, Object> fetchExistingPipeline(Map<String, Object> newPipeline) {
    String newPipelineID = (String) newPipeline.get("id");
    if (StringUtils.isNotEmpty(newPipelineID)) {
      try {
        return front50Service.getPipeline(newPipelineID);
      } catch (SpinnakerHttpException e) {
        // Return a null if pipeline with expected id not found
        if (e.getResponseCode() == HTTP_NOT_FOUND) {
          log.debug("Existing pipeline with id {} not found. Returning null.", newPipelineID);
        }
      }
    }
    return null;
  }

  private boolean runAsUserIsNullOrManagedServiceAccount(String runAsUser) {
    return runAsUser == null
        || runAsUser.endsWith(SavePipelineStage.SERVICE_ACCOUNT_SUFFIX)
        || runAsUser.endsWith(SavePipelineStage.SHARED_SERVICE_ACCOUNT_SUFFIX);
  }
}
