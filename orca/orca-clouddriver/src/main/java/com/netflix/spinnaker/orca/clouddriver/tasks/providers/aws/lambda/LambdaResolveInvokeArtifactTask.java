/*
 * Copyright 2026 Harness, Inc
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.lambda.LambdaStageConstants;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaHealthCheckArtifact;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaPipelineArtifact;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaTrafficUpdateInput;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import java.io.IOException;
import java.util.Collections;
import javax.annotation.Nonnull;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LambdaResolveInvokeArtifactTask implements Task {
  private static final Logger logger =
      LoggerFactory.getLogger(LambdaResolveInvokeArtifactTask.class);

  public static final String TASK_NAME = "lambdaResolveInvokeArtifact";

  private final ArtifactUtils artifactUtils;
  private final OortService oortService;
  private final ObjectMapper objectMapper;

  @Autowired
  public LambdaResolveInvokeArtifactTask(
      ArtifactUtils artifactUtils, OortService oortService, ObjectMapper objectMapper) {
    this.artifactUtils = artifactUtils;
    this.oortService = oortService;
    this.objectMapper = objectMapper;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    logger.debug("Executing LambdaResolveInvokeArtifactTask...");

    LambdaTrafficUpdateInput input =
        objectMapper.convertValue(stage.getContext(), LambdaTrafficUpdateInput.class);
    LambdaHealthCheckArtifact payloadArtifact = input == null ? null : input.getPayloadArtifact();

    if (payloadArtifact == null) {
      logger.debug("No payload artifact configured; skipping resolution.");
      return TaskResult.SUCCEEDED;
    }

    String artifactId = payloadArtifact.getId();
    LambdaPipelineArtifact defaultArtifact = payloadArtifact.getArtifact();

    if (StringUtils.isBlank(artifactId) && defaultArtifact == null) {
      logger.debug("Payload artifact is empty; skipping resolution.");
      return TaskResult.SUCCEEDED;
    }

    Artifact artifact =
        artifactUtils.getBoundArtifactForStage(
            stage,
            StringUtils.isBlank(artifactId) ? null : artifactId,
            toKorkArtifact(defaultArtifact));

    if (artifact == null || StringUtils.isBlank(artifact.getReference())) {
      throw new IllegalStateException("Could not resolve payload artifact for Lambda invoke.");
    }

    String payload = fetchArtifactContent(artifact);
    logger.debug("Resolved Lambda invoke payload artifact, content length: {}", payload.length());

    return TaskResult.builder(ExecutionStatus.SUCCEEDED)
        .context(
            ImmutableMap.of(
                LambdaStageConstants.resolvedPayloadKey,
                payload,
                LambdaStageConstants.resolvedPayloadArtifactKey,
                artifact))
        .outputs(Collections.singletonMap(LambdaStageConstants.resolvedPayloadKey, payload))
        .build();
  }

  private Artifact toKorkArtifact(LambdaPipelineArtifact artifact) {
    if (artifact == null) {
      return null;
    }
    return Artifact.builder()
        .uuid(artifact.getId())
        .artifactAccount(artifact.getArtifactAccount())
        .type(artifact.getType())
        .reference(artifact.getReference())
        .name(artifact.getName())
        .version(artifact.getVersion())
        .location(artifact.getLocation())
        .provenance(artifact.getProvenance())
        .build();
  }

  private String fetchArtifactContent(Artifact artifact) {
    try (ResponseBody body = Retrofit2SyncCall.execute(oortService.fetchArtifact(artifact))) {
      return body.string();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to fetch payload artifact content", e);
    }
  }
}
