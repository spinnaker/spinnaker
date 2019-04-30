/*
 * Copyright 2018 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.orca.pipeline.tasks.artifacts;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class BindProducedArtifactsTask implements Task {
  public static final String TASK_NAME = "bindProducedArtifacts";

  @Autowired
  ArtifactResolver artifactResolver;

  @Autowired
  ObjectMapper objectMapper;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    Map<String, Object> context = stage.getContext();
    Map<String, Object> outputs = new HashMap<>();

    List<ExpectedArtifact> expectedArtifacts = objectMapper.convertValue(
        context.get("expectedArtifacts"),
        new TypeReference<List<ExpectedArtifact>>() { }
    );

    if (expectedArtifacts == null || expectedArtifacts.isEmpty()) {
      return TaskResult.SUCCEEDED;
    }

    List<Artifact> artifacts = artifactResolver.getArtifacts(stage);
    Set<Artifact> resolvedArtifacts = artifactResolver.resolveExpectedArtifacts(expectedArtifacts, artifacts, false);

    outputs.put("artifacts", resolvedArtifacts);
    outputs.put("resolvedExpectedArtifacts", expectedArtifacts);

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).outputs(outputs).build();
  }
}
