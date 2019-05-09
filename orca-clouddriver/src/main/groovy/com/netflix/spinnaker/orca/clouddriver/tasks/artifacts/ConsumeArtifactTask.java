/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.orca.clouddriver.tasks.artifacts;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import lombok.NonNull;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

@Component
public class ConsumeArtifactTask implements Task {
  public static final String TASK_NAME = "consumeArtifact";

  private ArtifactResolver artifactResolver;
  private OortService oort;
  private RetrySupport retrySupport;
  private ObjectMapper objectMapper = new ObjectMapper();

  public ConsumeArtifactTask(ArtifactResolver artifactResolver, OortService oortService, RetrySupport retrySupport) {
    this.artifactResolver = artifactResolver;
    this.oort = oortService;
    this.retrySupport = retrySupport;
  }

  @Override
  public TaskResult execute(@NonNull Stage stage) {
    Map<String, Object> task = stage.getContext();
    String artifactId = (String) task.get("consumeArtifactId");

    Artifact artifact = artifactResolver.getBoundArtifactForId(stage, artifactId);
    if (artifact == null) {
      throw new IllegalArgumentException("No artifact could be bound to '" + artifactId + "'");
    }

    artifact.setArtifactAccount((String) task.get("consumeArtifactAccount"));

    InputStream fetchedArtifact = retrySupport.retry(() -> {
      Response artifactBody = oort.fetchArtifact(artifact);
      try {
        return artifactBody.getBody().in();
      } catch (Exception e) {
        throw new IllegalStateException("Failed to fetch artifact.");
      }
    }, 10, 200, true);

    try {
      Map<String, Object> parsed = objectMapper.readValue(fetchedArtifact, new TypeReference<Map<String, Object>>(){});

      // null values in the parsed result cause calls to TaskBuilder::context to throw an exception
      // so we remove them to avoid that.
      parsed.values().removeIf(Objects::isNull);

      return TaskResult.builder(ExecutionStatus.SUCCEEDED).outputs(parsed).build();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to deserialize artifact as JSON: " + e.getMessage());
    }

  }
}
