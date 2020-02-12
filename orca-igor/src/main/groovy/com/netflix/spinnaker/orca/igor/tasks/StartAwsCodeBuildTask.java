/*
 * Copyright 2020 Amazon.com, Inc.
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
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.igor.IgorService;
import com.netflix.spinnaker.orca.igor.model.AwsCodeBuildExecution;
import com.netflix.spinnaker.orca.igor.model.AwsCodeBuildStageDefinition;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StartAwsCodeBuildTask implements Task {
  private static final String PROJECT_NAME = "projectName";
  private static final String SOURCE_LOCATION = "sourceLocationOverride";
  private static final String SOURCE_TYPE = "sourceTypeOverride";
  private static final String SOURCE_VERSION = "sourceVersion";
  private static final String ENV_VARS = "environmentVariablesOverride";
  private static final String ENV_VAR_TYPE = "type";
  private static final String ENV_VAR_NAME = "name";
  private static final String ENV_VAR_VALUE = "value";

  private final IgorService igorService;
  private final ArtifactUtils artifactUtils;

  @Override
  @Nonnull
  public TaskResult execute(@Nonnull Stage stage) {
    AwsCodeBuildStageDefinition stageDefinition = stage.mapTo(AwsCodeBuildStageDefinition.class);

    Map<String, Object> requestInput = new HashMap<>();
    appendProjectName(requestInput, stageDefinition.getProjectName());
    if (stageDefinition.isSourceOverride() && stageDefinition.getSource() != null) {
      appendSource(
          requestInput,
          getSourceArtifact(stage, stageDefinition),
          stageDefinition.getSource().getSourceType());
    }
    // sourceVersion takes precedence of version in source artifact
    appendSourceVersion(requestInput, stageDefinition.getSourceVersion());
    appendEnvironmentVariables(requestInput, stageDefinition.getEnvironmentVariables());

    AwsCodeBuildExecution execution =
        igorService.startAwsCodeBuild(stageDefinition.getAccount(), requestInput);

    Map<String, Object> context = stage.getContext();
    context.put("buildInfo", execution);
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(context).build();
  }

  private void appendProjectName(Map<String, Object> requestInput, String projectName) {
    requestInput.put(PROJECT_NAME, projectName);
  }

  private void appendSource(
      Map<String, Object> requestInput, Artifact artifact, String sourceType) {
    if (sourceType != null && !sourceType.equals("")) {
      requestInput.put(SOURCE_TYPE, sourceType);
    }
    switch (artifact.getType()) {
      case "s3/object":
        requestInput.putIfAbsent(SOURCE_TYPE, "S3");
        String s3Location = artifact.getReference();
        requestInput.put(
            SOURCE_LOCATION, s3Location.startsWith("s3://") ? s3Location.substring(5) : s3Location);
        break;
      case "git/repo":
        requestInput.put(SOURCE_LOCATION, artifact.getReference());
        if (!requestInput.containsKey(SOURCE_TYPE)) {
          if (artifact.getReference().matches("^(http(s)?://)github.com/(.*)$")) {
            requestInput.put(SOURCE_TYPE, "GITHUB");
          } else if (artifact.getReference().matches("^(http(s)?://)(.*@)?bitbucket.org/(.*)$")) {
            requestInput.put(SOURCE_TYPE, "BITBUCKET");
          } else if (artifact
              .getReference()
              .matches("^https://git-codecommit.(.*).amazonaws.com/(.*)$")) {
            requestInput.put(SOURCE_TYPE, "CODECOMMIT");
          } else {
            throw new IllegalStateException("Source type could not be inferred from location");
          }
        }
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + artifact.getType());
    }
    if (artifact.getVersion() != null) {
      requestInput.put(SOURCE_VERSION, artifact.getVersion());
    }
  }

  private void appendSourceVersion(Map<String, Object> requestInput, String sourceVersion) {
    if (sourceVersion != null && !sourceVersion.equals("")) {
      requestInput.put(SOURCE_VERSION, sourceVersion);
    }
  }

  private void appendEnvironmentVariables(
      Map<String, Object> requestInput, Map<String, String> environmentVariables) {
    if (environmentVariables == null) {
      return;
    }
    List<Map<String, String>> startBuildEnvVars = new ArrayList<>();
    for (String key : environmentVariables.keySet()) {
      Map<String, String> envVar = new HashMap<>();
      envVar.put(ENV_VAR_TYPE, "PLAINTEXT");
      envVar.put(ENV_VAR_NAME, key);
      envVar.put(ENV_VAR_VALUE, environmentVariables.get(key));
      startBuildEnvVars.add(envVar);
    }
    requestInput.put(ENV_VARS, startBuildEnvVars);
  }

  private Artifact getSourceArtifact(
      @Nonnull Stage stage, AwsCodeBuildStageDefinition stageDefinition) {
    Artifact artifact =
        artifactUtils.getBoundArtifactForStage(
            stage,
            stageDefinition.getSource().getArtifactId(),
            stageDefinition.getSource().getArtifact());

    if (artifact == null) {
      throw new IllegalArgumentException("No artifact was specified.");
    }

    if (artifact.getArtifactAccount() == null) {
      throw new IllegalArgumentException("No artifact account was specified.");
    }

    if (artifact.getMetadata() != null && artifact.getMetadata().containsKey("subPath")) {
      throw new IllegalArgumentException("Subpath is not supported by AWS CodeBuild stage");
    }
    return artifact;
  }
}
