/*
 * Copyright 2019 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.igor.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.igor.IgorService;
import com.netflix.spinnaker.orca.igor.model.GoogleCloudBuild;
import com.netflix.spinnaker.orca.igor.model.GoogleCloudBuildStageDefinition;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import retrofit.client.Response;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class StartGoogleCloudBuildTask implements Task {
  private final IgorService igorService;
  private final OortService oortService;
  private final ArtifactResolver artifactResolver;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final RetrySupport retrySupport = new RetrySupport();
  private static final ThreadLocal<Yaml> yamlParser = ThreadLocal.withInitial(() -> new Yaml(new SafeConstructor()));

  @Override
  @Nonnull public TaskResult execute(@Nonnull Stage stage) {
    GoogleCloudBuildStageDefinition stageDefinition = stage.mapTo(GoogleCloudBuildStageDefinition.class);

    Map<String, Object> buildDefinition;
    if (stageDefinition.getBuildDefinitionSource() != null && stageDefinition.getBuildDefinitionSource().equals("artifact")) {
      buildDefinition = getBuildDefinitionFromArtifact(stage, stageDefinition);
    } else {
      buildDefinition = stageDefinition.getBuildDefinition();
    }
    GoogleCloudBuild result = igorService.createGoogleCloudBuild(stageDefinition.getAccount(), buildDefinition);
    Map<String, Object> context = stage.getContext();
    context.put("buildInfo", result);
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(context).build();
  }

  private Map<String, Object> getBuildDefinitionFromArtifact(@Nonnull Stage stage, GoogleCloudBuildStageDefinition stageDefinition) {
    Artifact buildDefinitionArtifact = artifactResolver.getBoundArtifactForStage(stage, stageDefinition.getBuildDefinitionArtifact().getArtifactId(),
      stageDefinition.getBuildDefinitionArtifact().getArtifact());

    if (buildDefinitionArtifact == null) {
      throw new IllegalArgumentException("No manifest artifact was specified.");
    }

    if(stageDefinition.getBuildDefinitionArtifact().getArtifactAccount() != null) {
      buildDefinitionArtifact.setArtifactAccount(stageDefinition.getBuildDefinitionArtifact().getArtifactAccount());
    }

    if (buildDefinitionArtifact.getArtifactAccount() == null) {
      throw new IllegalArgumentException("No manifest artifact account was specified.");
    }

    return retrySupport.retry(() -> {
      try {
        Response buildText = oortService.fetchArtifact(buildDefinitionArtifact);
        Object result = yamlParser.get().load(buildText.getBody().in());
        return (Map<String, Object>) result;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }, 10, 200, false);
  }
}

