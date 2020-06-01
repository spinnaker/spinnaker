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

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.igor.IgorService;
import com.netflix.spinnaker.orca.igor.model.GoogleCloudBuild;
import com.netflix.spinnaker.orca.igor.model.GoogleCloudBuildStageDefinition;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import java.io.IOException;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import retrofit.client.Response;

@Component
@RequiredArgsConstructor
public class StartGoogleCloudBuildTask implements Task {
  private final IgorService igorService;
  private final OortService oortService;
  private final ArtifactUtils artifactUtils;
  private final ContextParameterProcessor contextParameterProcessor;

  private final RetrySupport retrySupport = new RetrySupport();
  private static final ThreadLocal<Yaml> yamlParser =
      ThreadLocal.withInitial(() -> new Yaml(new SafeConstructor()));

  @Override
  @Nonnull
  public TaskResult execute(@Nonnull StageExecution stage) {
    GoogleCloudBuildStageDefinition stageDefinition =
        stage.mapTo(GoogleCloudBuildStageDefinition.class);

    GoogleCloudBuild result;
    switch (stageDefinition.getBuildDefinitionSource()) {
      case ARTIFACT:
        result =
            igorService.createGoogleCloudBuild(
                stageDefinition.getAccount(),
                getBuildDefinitionFromArtifact(stage, stageDefinition));
        break;
      case TRIGGER:
        result =
            igorService.runGoogleCloudBuildTrigger(
                stageDefinition.getAccount(),
                stageDefinition.getTriggerId(),
                stageDefinition.getRepoSource());
        break;
      default:
        result =
            igorService.createGoogleCloudBuild(
                stageDefinition.getAccount(), stageDefinition.getBuildDefinition());
    }

    Map<String, Object> context = stage.getContext();
    context.put("buildInfo", result);
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(context).build();
  }

  private Artifact getBuildDefinitionArtifact(
      @Nonnull StageExecution stage, GoogleCloudBuildStageDefinition stageDefinition) {
    Artifact buildDefinitionArtifact =
        artifactUtils.getBoundArtifactForStage(
            stage,
            stageDefinition.getBuildDefinitionArtifact().getArtifactId(),
            stageDefinition.getBuildDefinitionArtifact().getArtifact());

    if (buildDefinitionArtifact == null) {
      throw new IllegalArgumentException("No manifest artifact was specified.");
    }

    buildDefinitionArtifact =
        ArtifactUtils.withAccount(
            buildDefinitionArtifact,
            stageDefinition.getBuildDefinitionArtifact().getArtifactAccount());
    if (buildDefinitionArtifact.getArtifactAccount() == null) {
      throw new IllegalArgumentException("No manifest artifact account was specified.");
    }
    return buildDefinitionArtifact;
  }

  private Map<String, Object> getBuildDefinitionFromArtifact(
      @Nonnull StageExecution stage, GoogleCloudBuildStageDefinition stageDefinition) {
    final Artifact buildDefinitionArtifact = getBuildDefinitionArtifact(stage, stageDefinition);
    Map<String, Object> buildDefinition =
        retrySupport.retry(
            () -> {
              try {
                Response buildText = oortService.fetchArtifact(buildDefinitionArtifact);
                Object result = yamlParser.get().load(buildText.getBody().in());
                return (Map<String, Object>) result;
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            },
            10,
            200,
            false);

    return contextParameterProcessor.process(
        buildDefinition, contextParameterProcessor.buildExecutionContext(stage), true);
  }
}
