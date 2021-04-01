/*
 * Copyright 2020 Armory, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.appengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class DeployAppEngineConfigurationTask implements CloudProviderAware, RetryableTask {

  private final ObjectMapper objectMapper;
  private final KatoService kato;
  private final String CLOUD_OPERATION_TYPE = "deployAppengineConfiguration";
  private final String CLOUD_PROVIDER = "appengine";
  private final ArtifactUtils artifactUtils;

  @Override
  public long getBackoffPeriod() {
    return 30000;
  }

  @Override
  public long getTimeout() {
    return 300000;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    Map<String, Object> context = stage.getContext();
    Map<String, Object> operationDescription = new HashMap<>();
    String account = getCredentials(stage);
    operationDescription.put("accountName", account);

    if (context.get("cronArtifact") == null
        && context.get("dispatchArtifact") == null
        && context.get("indexArtifact") == null
        && context.get("queueArtifact") == null) {
      throw new IllegalArgumentException("At least one configuration artifact must be supplied.");
    }

    if (context.get("cronArtifact") != null) {
      ArtifactAccountPair artifactAccountPair =
          objectMapper.convertValue(context.get("cronArtifact"), ArtifactAccountPair.class);
      Artifact artifact =
          artifactUtils.getBoundArtifactForStage(
              stage,
              artifactAccountPair.getId(),
              objectMapper.convertValue(artifactAccountPair.getArtifact(), Artifact.class));
      operationDescription.put("cronArtifact", artifact);
    }

    if (context.get("dispatchArtifact") != null) {
      ArtifactAccountPair artifactAccountPair =
          objectMapper.convertValue(context.get("dispatchArtifact"), ArtifactAccountPair.class);
      Artifact artifact =
          artifactUtils.getBoundArtifactForStage(
              stage,
              artifactAccountPair.getId(),
              objectMapper.convertValue(artifactAccountPair.getArtifact(), Artifact.class));
      operationDescription.put("dispatchArtifact", artifact);
    }

    if (context.get("indexArtifact") != null) {
      ArtifactAccountPair artifactAccountPair =
          objectMapper.convertValue(context.get("indexArtifact"), ArtifactAccountPair.class);
      Artifact artifact =
          artifactUtils.getBoundArtifactForStage(
              stage,
              artifactAccountPair.getId(),
              objectMapper.convertValue(artifactAccountPair.getArtifact(), Artifact.class));
      operationDescription.put("indexArtifact", artifact);
    }

    if (context.get("queueArtifact") != null) {
      ArtifactAccountPair artifactAccountPair =
          objectMapper.convertValue(context.get("queueArtifact"), ArtifactAccountPair.class);
      Artifact artifact =
          artifactUtils.getBoundArtifactForStage(
              stage,
              artifactAccountPair.getId(),
              objectMapper.convertValue(artifactAccountPair.getArtifact(), Artifact.class));
      operationDescription.put("queueArtifact", artifact);
    }

    Map<String, Map> operation =
        new ImmutableMap.Builder<String, Map>()
            .put(CLOUD_OPERATION_TYPE, operationDescription)
            .build();

    TaskId taskId = kato.requestOperations(CLOUD_PROVIDER, Collections.singletonList(operation));

    Map<String, Object> outputs =
        new ImmutableMap.Builder<String, Object>()
            .put("notification.type", CLOUD_OPERATION_TYPE)
            .put("kato.last.task.id", taskId)
            .put("service.region", Optional.ofNullable(stage.getContext().get("region")).orElse(""))
            .put("service.account", account)
            .build();
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build();
  }
}
