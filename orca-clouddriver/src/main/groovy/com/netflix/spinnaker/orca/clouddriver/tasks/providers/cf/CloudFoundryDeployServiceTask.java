/*
 *  Copyright 2019 Pivotal, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.*;

@RequiredArgsConstructor
@Component
public class CloudFoundryDeployServiceTask extends AbstractCloudProviderAwareTask {
  private final KatoService kato;
  private final ArtifactResolver artifactResolver;
  private final ObjectMapper artifactMapper = new ObjectMapper()
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    String cloudProvider = getCloudProvider(stage);
    String account = getCredentials(stage);
    Map<String, Object> context = bindArtifactIfNecessary(stage);
    Map<String, Map> operation = new ImmutableMap.Builder<String, Map>()
      .put("deployService", context)
      .build();
    TaskId taskId = kato.requestOperations(cloudProvider, Collections.singletonList(operation)).toBlocking().first();
    Map<String, Object> outputs = new ImmutableMap.Builder<String, Object>()
      .put("notification.type", "deployService")
      .put("kato.last.task.id", taskId)
      .put("service.region",  stage.getContext().get("region"))
      .put("service.account", account)
      .build();
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build();
  }

  @NotNull
  private Map<String, Object> bindArtifactIfNecessary(@Nonnull Stage stage) {
    Map<String, Object> context = stage.getContext();
    Map manifest = (Map) context.get("manifest");
    if(manifest.get("artifactId") != null || manifest.get("artifact") != null) {
      Artifact artifact = manifest.get("artifact") != null ?
        artifactMapper.convertValue(manifest.get("artifact"), Artifact.class) :
        null;
      Artifact boundArtifact = artifactResolver.getBoundArtifactForStage(stage, (String) manifest.get("artifactId"), artifact);
      if(boundArtifact == null) {
        throw new IllegalArgumentException("Unable to bind the service manifest artifact");
      }
      manifest.remove("artifactId"); // replacing with the bound artifact now
      //noinspection unchecked
      manifest.put("artifact", artifactMapper.convertValue(boundArtifact, Map.class));
    }
    return context;
  }
}
