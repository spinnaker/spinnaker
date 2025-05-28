/*
 * Copyright 2021 OpsMx, Inc.
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.PipelineModelMutator;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import retrofit2.Response;

@Component
public class DeletePipelineTask implements CloudProviderAware, RetryableTask {

  private Logger log = LoggerFactory.getLogger(getClass());

  @Autowired
  DeletePipelineTask(
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

  @Override
  public TaskResult execute(StageExecution stage) {
    if (front50Service == null) {
      throw new UnsupportedOperationException(
          "Front50 is not enabled, no way to delete pipeline. Fix this by setting front50.enabled: true");
    }

    if (!stage.getContext().containsKey("pipeline")) {
      throw new IllegalArgumentException("pipeline context must be provided");
    }

    Map<String, Object> pipeline;
    if (!(stage.getContext().get("pipeline") instanceof String)) {
      pipeline = (Map<String, Object>) stage.getContext().get("pipeline");
    } else {
      pipeline = (Map<String, Object>) stage.decodeBase64("/pipeline", Map.class);
    }

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
    final Boolean isSavingMultiplePipelines =
        (Boolean)
            Optional.ofNullable(stage.getContext().get("isSavingMultiplePipelines")).orElse(false);
    final Boolean staleCheck =
        (Boolean) Optional.ofNullable(stage.getContext().get("staleCheck")).orElse(false);
    if (stage.getContext().get("pipeline.id") != null
        && pipeline.get("id") == null
        && !isSavingMultiplePipelines) {
      pipeline.put("id", stage.getContext().get("pipeline.id"));

      // We need to tell front50 to regenerate cron trigger id's
      pipeline.put("regenerateCronTriggerIds", true);
    }

    pipelineModelMutators.stream()
        .filter(m -> m.supports(pipeline))
        .forEach(m -> m.mutate(pipeline));

    Response<ResponseBody> response =
        Retrofit2SyncCall.executeCall(
            front50Service.deletePipeline(
                pipeline.get("application").toString(), pipeline.get("name").toString()));

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("notification.type", "deletepipeline");
    outputs.put("application", pipeline.get("application"));
    outputs.put("pipeline.name", pipeline.get("name"));

    try (ResponseBody body = response.body()) {
      Map<String, Object> savedPipeline =
          objectMapper.readValue(body.byteStream(), new TypeReference<>() {});
      outputs.put("pipeline.id", savedPipeline.get("id"));
    } catch (Exception e) {
      log.error("Unable to deserialize saved pipeline, reason: ", e);

      if (pipeline.containsKey("id")) {
        outputs.put("pipeline.id", pipeline.get("id"));
      }
    }

    final ExecutionStatus status;
    if (response.code() == HttpStatus.OK.value()) {
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
  }

  private Map<String, Object> fetchExistingPipeline(Map<String, Object> newPipeline) {
    String applicationName = (String) newPipeline.get("application");
    String newPipelineID = (String) newPipeline.get("id");
    if (!StringUtils.isEmpty(newPipelineID)) {
      return Retrofit2SyncCall.execute(front50Service.getPipelines(applicationName)).stream()
          .filter(m -> m.containsKey("id"))
          .filter(m -> m.get("id").equals(newPipelineID))
          .findFirst()
          .orElse(null);
    }
    return null;
  }
}
