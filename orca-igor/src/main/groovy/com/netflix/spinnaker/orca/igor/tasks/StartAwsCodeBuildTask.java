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
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.igor.IgorService;
import com.netflix.spinnaker.orca.igor.model.AwsCodeBuildExecution;
import com.netflix.spinnaker.orca.igor.model.AwsCodeBuildStageDefinition;
import com.netflix.spinnaker.orca.igor.model.AwsCodeBuildStageDefinition.AwsCodeBuildSource;
import com.netflix.spinnaker.orca.igor.model.AwsCodeBuildStageDefinition.AwsCodeBuildSourceArtifact;
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
  private static final String IMAGE_LOCATION = "imageOverride";
  private static final String IMAGE_CREDENTIALS_TYPE = "imagePullCredentialsTypeOverride";
  private static final String BUILDSPEC = "buildspecOverride";
  private static final String SECONDARY_SOURCES = "secondarySourcesOverride";
  private static final String SECONDARY_SOURCES_VERSION = "secondarySourcesVersionOverride";
  private static final String SECONDARY_SOURCE_TYPE = "type";
  private static final String SECONDARY_SOURCE_LOCATION = "location";
  private static final String SECONDARY_SOURCE_IDENTIFIER = "sourceIdentifier";
  private static final String SECONDARY_SOURCE_VERSION = "sourceVersion";
  private static final String ENV_VARS = "environmentVariablesOverride";
  private static final String ENV_VAR_TYPE = "type";
  private static final String ENV_VAR_NAME = "name";
  private static final String ENV_VAR_VALUE = "value";

  private final IgorService igorService;
  private final ArtifactUtils artifactUtils;

  @Override
  @Nonnull
  public TaskResult execute(@Nonnull StageExecution stage) {
    AwsCodeBuildStageDefinition stageDefinition = stage.mapTo(AwsCodeBuildStageDefinition.class);

    Map<String, Object> requestInput = new HashMap<>();
    appendProjectName(requestInput, stageDefinition.getProjectName());

    appendSource(requestInput, stage, stageDefinition.getSource());

    appendSecondarySources(requestInput, stage, stageDefinition.getSecondarySources());

    appendImage(requestInput, stageDefinition.getImage());
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
      Map<String, Object> requestInput, StageExecution stage, AwsCodeBuildSource source) {
    if (source != null) {
      Artifact matchArtifact = Artifact.builder().build();
      if (source.isSourceOverride() && source.getSourceArtifact() != null) {
        matchArtifact = getSourceArtifact(stage, source.getSourceArtifact());
        if (source.getSourceType() != null && !source.getSourceType().equals("")) {
          requestInput.put(SOURCE_TYPE, source.getSourceType());
        } else {
          requestInput.put(SOURCE_TYPE, getSourceType(matchArtifact));
        }
        requestInput.put(SOURCE_LOCATION, getSourceLocation(matchArtifact));
      }

      if (source.getSourceVersion() != null && !source.getSourceVersion().equals("")) {
        requestInput.put(SOURCE_VERSION, source.getSourceVersion());
      } else if (matchArtifact.getVersion() != null && !matchArtifact.getVersion().equals("")) {
        requestInput.put(SOURCE_VERSION, matchArtifact.getVersion());
      }

      if (source.getBuildspec() != null && !source.getBuildspec().equals("")) {
        requestInput.put(BUILDSPEC, source.getBuildspec());
      }
    }
  }

  private void appendSecondarySources(
      Map<String, Object> requestInput, StageExecution stage, List<AwsCodeBuildSource> sources) {
    if (sources != null) {
      List<Map<String, String>> secondarySources = new ArrayList<>();
      List<Map<String, String>> secondarySourcesVersion = new ArrayList<>();
      for (AwsCodeBuildSource source : sources) {
        Artifact matchArtifact = getSourceArtifact(stage, source.getSourceArtifact());
        appendSecondarySource(secondarySources, secondarySourcesVersion, matchArtifact, source);
      }
      requestInput.put(SECONDARY_SOURCES, secondarySources);
      requestInput.put(SECONDARY_SOURCES_VERSION, secondarySourcesVersion);
    }
  }

  private void appendSecondarySource(
      List<Map<String, String>> secondarySources,
      List<Map<String, String>> secondarySourcesVersion,
      Artifact matchArtifact,
      AwsCodeBuildSource artifact) {
    HashMap<String, String> source = new HashMap<>();
    HashMap<String, String> sourceVersion = new HashMap<>();
    if (artifact.getSourceType() != null && !artifact.getSourceType().equals("")) {
      source.put(SECONDARY_SOURCE_TYPE, artifact.getSourceType());
    } else {
      source.put(SECONDARY_SOURCE_TYPE, getSourceType(matchArtifact));
    }
    source.put(SECONDARY_SOURCE_LOCATION, getSourceLocation(matchArtifact));

    String identifier = String.valueOf(secondarySources.size());
    source.put(SECONDARY_SOURCE_IDENTIFIER, identifier);

    if (artifact.getSourceVersion() != null && !artifact.getSourceVersion().equals("")) {
      sourceVersion.put(SECONDARY_SOURCE_IDENTIFIER, identifier);
      sourceVersion.put(SECONDARY_SOURCE_VERSION, artifact.getSourceVersion());
    } else if (matchArtifact.getVersion() != null && !matchArtifact.getVersion().equals("")) {
      sourceVersion.put(SECONDARY_SOURCE_IDENTIFIER, identifier);
      sourceVersion.put(SECONDARY_SOURCE_VERSION, matchArtifact.getVersion());
    }

    secondarySources.add(source);
    if (!sourceVersion.isEmpty()) {
      secondarySourcesVersion.add(sourceVersion);
    }
  }

  private void appendImage(Map<String, Object> requestInput, String image) {
    if (image != null && !image.equals("")) {
      requestInput.put(IMAGE_LOCATION, image);
      requestInput.put(IMAGE_CREDENTIALS_TYPE, "SERVICE_ROLE");
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
      @Nonnull StageExecution stage, AwsCodeBuildSourceArtifact artifact) {
    Artifact matchArtifact =
        artifactUtils.getBoundArtifactForStage(
            stage, artifact.getArtifactId(), artifact.getArtifact());

    if (matchArtifact == null) {
      throw new IllegalArgumentException("No artifact was specified.");
    }

    if (matchArtifact.getArtifactAccount() == null) {
      throw new IllegalArgumentException("No artifact account was specified.");
    }

    if (matchArtifact.getMetadata() != null && matchArtifact.getMetadata().containsKey("subPath")) {
      throw new IllegalArgumentException("Subpath is not supported by AWS CodeBuild stage");
    }
    return matchArtifact;
  }

  private String getSourceType(Artifact artifact) {
    switch (artifact.getType()) {
      case "s3/object":
        return "S3";
      case "git/repo":
        if (artifact.getReference().matches("^(http(s)?://)github.com/(.*)$")) {
          return "GITHUB";
        } else if (artifact.getReference().matches("^(http(s)?://)(.*@)?bitbucket.org/(.*)$")) {
          return "BITBUCKET";
        } else if (artifact
            .getReference()
            .matches("^https://git-codecommit.(.*).amazonaws.com/(.*)$")) {
          return "CODECOMMIT";
        } else {
          throw new IllegalStateException("Source type could not be inferred from location");
        }
      default:
        throw new IllegalStateException("Unexpected value: " + artifact.getType());
    }
  }

  private String getSourceLocation(Artifact artifact) {
    switch (artifact.getType()) {
      case "s3/object":
        String s3Location = artifact.getReference();
        return s3Location.startsWith("s3://") ? s3Location.substring(5) : s3Location;
      case "git/repo":
        return artifact.getReference();
      default:
        throw new IllegalStateException("Unexpected value: " + artifact.getType());
    }
  }
}
