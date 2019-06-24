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

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FindArtifactFromExecutionTask implements Task {
  public static final String TASK_NAME = "findArtifactFromExecution";

  private final ArtifactResolver artifactResolver;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    FindArtifactFromExecutionContext context = stage.mapTo(FindArtifactFromExecutionContext.class);
    Map<String, Object> outputs = new HashMap<>();
    String pipeline = context.getPipeline();
    List<ExpectedArtifact> expectedArtifacts = context.getExpectedArtifacts();
    FindArtifactFromExecutionContext.ExecutionOptions executionOptions =
        context.getExecutionOptions();

    List<Artifact> priorArtifacts;
    // never resolve artifacts from the same stage in a prior execution
    // we will get the set of the artifacts and remove them from the collection
    String pipelineConfigId =
        Optional.ofNullable(stage.getExecution().getPipelineConfigId()).orElse("");
    if (pipelineConfigId.equals(pipeline)) {
      priorArtifacts =
          artifactResolver.getArtifactsForPipelineIdWithoutStageRef(
              pipeline, stage.getRefId(), executionOptions.toCriteria());
    } else {
      priorArtifacts =
          artifactResolver.getArtifactsForPipelineId(pipeline, executionOptions.toCriteria());
    }

    Set<Artifact> matchingArtifacts =
        artifactResolver.resolveExpectedArtifacts(expectedArtifacts, priorArtifacts, null, false);

    outputs.put("resolvedExpectedArtifacts", expectedArtifacts);
    outputs.put("artifacts", matchingArtifacts);

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).outputs(outputs).build();
  }
}
