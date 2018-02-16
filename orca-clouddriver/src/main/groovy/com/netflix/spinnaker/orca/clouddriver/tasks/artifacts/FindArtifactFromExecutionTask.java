/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.artifacts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class FindArtifactFromExecutionTask implements Task {
  public static final String TASK_NAME = "findArtifactFromExecution";

  @Autowired
  ArtifactResolver artifactResolver;

  @Autowired
  ObjectMapper objectMapper;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    Map<String, Object> context = stage.getContext();
    Map<String, Object> outputs = new HashMap<>();
    String pipeline = (String) context.get("pipeline");
    ExpectedArtifact expectedArtifact = objectMapper.convertValue(context.get("expectedArtifact"), ExpectedArtifact.class);
    ExecutionOptions executionOptions = objectMapper.convertValue(context.get("executionOptions"), ExecutionOptions.class);

    List<Artifact> priorArtifacts = artifactResolver.getArtifactsForPipelineId(pipeline, executionOptions.toCriteria());

    Artifact match = artifactResolver.resolveSingleArtifact(expectedArtifact, priorArtifacts, false);

    if (match == null) {
      outputs.put("exception", "No artifact matching " + expectedArtifact + " found among " + priorArtifacts);
      return new TaskResult(ExecutionStatus.TERMINAL, new HashMap<>(), outputs);
    }

    outputs.put("resolvedExpectedArtifacts", Collections.singletonList(expectedArtifact));
    outputs.put("artifacts", Collections.singletonList(match));

    return new TaskResult(ExecutionStatus.SUCCEEDED, outputs, outputs);
  }

  @Data
  private static class ExecutionOptions {
    boolean succeeded;
    boolean terminal;
    boolean running;

    ExecutionCriteria toCriteria() {
      List<String> statuses = new ArrayList<>();
      if (succeeded) {
        statuses.add("SUCCEEDED");
      }

      if (terminal) {
        statuses.add("TERMINAL");
      }

      if (running) {
        statuses.add("RUNNING");
      }

      return new ExecutionCriteria().setStatuses(statuses);
    }
  }
}
