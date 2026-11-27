/*
 * Copyright 2026 Wise, PLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.objectstorage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Task that submits an uploadArtifactToObjectStorage operation to Clouddriver.
 *
 * <p>This task:
 *
 * <ol>
 *   <li>Resolves the artifact from the stage context using ArtifactUtils
 *   <li>Constructs the operation payload with credentials, region, bucket, objectKeyPrefix, and
 *       artifact
 *   <li>Submits the operation to Clouddriver via KatoService
 *   <li>Returns the Kato task ID for MonitorKatoTask to track
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadArtifactToObjectStorageTask implements CloudProviderAware, Task {

  private static final String OPERATION_NAME = "uploadArtifactToObjectStorage";

  private final KatoService katoService;
  private final ArtifactUtils artifactUtils;
  private final ObjectMapper objectMapper;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    Map<String, Object> context = stage.getContext();
    String cloudProvider = getCloudProvider(stage);
    String credentials = getCredentials(stage);

    log.info(
        "Executing uploadArtifactToObjectStorage for account {} in region {}",
        credentials,
        context.get("region"));

    List<Map<String, Map>> operations = getOperationsRequest(stage);
    TaskId taskId = katoService.requestOperations(cloudProvider, operations);

    log.info("Submitted {} operation to Clouddriver, task ID: {}", OPERATION_NAME, taskId.getId());

    Map<String, Object> outputs =
        new ImmutableMap.Builder<String, Object>()
            .put("notification.type", OPERATION_NAME)
            .put("kato.result.expected", true)
            .put("kato.last.task.id", taskId)
            .build();

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build();
  }

  public List<Map<String, Map>> getOperationsRequest(StageExecution stage) {
    Map<String, Object> context = stage.getContext();
    String credentials = getCredentials(stage);
    Artifact artifact = resolveArtifact(stage, context);

    Objects.requireNonNull(credentials, "'credentials' is required in stage context");
    Objects.requireNonNull(context.get("region"), "'region' is required in stage context");
    Objects.requireNonNull(context.get("bucket"), "'bucket' is required in stage context");

    Map<String, Object> operationPayload = new HashMap<>();
    operationPayload.put("credentials", credentials);
    operationPayload.put("region", context.get("region"));
    operationPayload.put("bucket", context.get("bucket"));
    operationPayload.put(
        "objectKeyPrefix",
        Optional.ofNullable(context.get("objectKeyPrefix")).map(Object::toString).orElse(""));
    operationPayload.put("artifact", artifact);
    operationPayload.put("deleteStaleFiles", Boolean.TRUE.equals(context.get("deleteStaleFiles")));
    operationPayload.put("forceUpload", Boolean.TRUE.equals(context.get("forceUpload")));
    operationPayload.put(
        "extractArchive", Boolean.TRUE.equals(context.getOrDefault("extractArchive", true)));

    String cacheControl =
        Optional.ofNullable(context.get("cacheControl")).map(Object::toString).orElse(null);
    if (cacheControl != null && !cacheControl.isBlank()) {
      operationPayload.put("cacheControl", cacheControl);
    }

    String contentType =
        Optional.ofNullable(context.get("contentType")).map(Object::toString).orElse(null);
    if (contentType != null && !contentType.isBlank()) {
      operationPayload.put("contentType", contentType);
    }

    String expires = Optional.ofNullable(context.get("expires")).map(Object::toString).orElse(null);
    if (expires != null && !expires.isBlank()) {
      operationPayload.put("expires", expires);
    }

    return List.of(Map.of(OPERATION_NAME, operationPayload));
  }

  /**
   * Resolves the artifact from the stage context.
   *
   * <p>Supports two modes:
   *
   * <ul>
   *   <li>artifactId: references an expected artifact from the pipeline
   *   <li>artifact: inline artifact definition
   * </ul>
   */
  private Artifact resolveArtifact(StageExecution stage, Map<String, Object> context) {
    String artifactId =
        Optional.ofNullable(context.get("artifactId")).map(Object::toString).orElse(null);

    Artifact inlineArtifact = null;
    if (context.get("artifact") != null) {
      inlineArtifact = objectMapper.convertValue(context.get("artifact"), Artifact.class);
    }

    Artifact resolvedArtifact =
        artifactUtils.getBoundArtifactForStage(stage, artifactId, inlineArtifact);

    if (resolvedArtifact == null) {
      throw new IllegalArgumentException(
          "No artifact could be resolved. Provide either 'artifactId' or 'artifact' in stage context.");
    }

    log.debug("Resolved artifact: {} ({})", resolvedArtifact.getName(), resolvedArtifact.getType());
    return resolvedArtifact;
  }
}
