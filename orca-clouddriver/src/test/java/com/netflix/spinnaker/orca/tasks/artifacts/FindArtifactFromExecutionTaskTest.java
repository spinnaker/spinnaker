/*
 * Copyright 2020 Google, Inc.
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

package com.netflix.spinnaker.orca.tasks.artifacts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.tasks.artifacts.FindArtifactFromExecutionTask;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class FindArtifactFromExecutionTaskTest {
  private static final String PIPELINE = "my pipeline";
  private static final Artifact ARTIFACT_A =
      Artifact.builder().type("kubernetes/replicaSet").build();
  private static final Artifact ARTIFACT_B =
      Artifact.builder().type("kubernetes/configMap").build();
  private static final ExpectedArtifact EXPECTED_ARTIFACT_A =
      ExpectedArtifact.builder().matchArtifact(ARTIFACT_A).build();
  private static final ExpectedArtifact EXPECTED_ARTIFACT_B =
      ExpectedArtifact.builder().matchArtifact(ARTIFACT_B).build();

  @Test
  void findsSingleArtifact() {
    ImmutableList<ExpectedArtifact> expectedArtifacts = ImmutableList.of(EXPECTED_ARTIFACT_A);
    ImmutableList<Artifact> pipelineArtifacts = ImmutableList.of(ARTIFACT_A, ARTIFACT_B);
    LinkedHashSet<Artifact> resolvedArtifacts = new LinkedHashSet<>(ImmutableSet.of(ARTIFACT_A));
    Stage stage =
        new Stage(
            mock(Execution.class), "findArtifactFromExecution", getStageContext(expectedArtifacts));

    ArtifactUtils artifactUtils = mock(ArtifactUtils.class);
    when(artifactUtils.getArtifactsForPipelineId(
            eq(PIPELINE), any(ExecutionRepository.ExecutionCriteria.class)))
        .thenReturn(pipelineArtifacts);
    when(artifactUtils.resolveExpectedArtifacts(expectedArtifacts, pipelineArtifacts, null, false))
        .thenReturn(resolvedArtifacts);

    FindArtifactFromExecutionTask task = new FindArtifactFromExecutionTask(artifactUtils);
    TaskResult result = task.execute(stage);

    Collection<ExpectedArtifact> resolvedExpectedArtifacts =
        (Collection<ExpectedArtifact>) result.getContext().get("resolvedExpectedArtifacts");
    assertThat(resolvedExpectedArtifacts).containsExactly(EXPECTED_ARTIFACT_A);

    Collection<Artifact> artifacts = (Collection<Artifact>) result.getContext().get("artifacts");
    assertThat(artifacts).containsExactly(ARTIFACT_A);
  }

  @Test
  void findsMultipleArtifacts() {
    ImmutableList<ExpectedArtifact> expectedArtifacts =
        ImmutableList.of(EXPECTED_ARTIFACT_A, EXPECTED_ARTIFACT_B);
    ImmutableList<Artifact> pipelineArtifacts = ImmutableList.of(ARTIFACT_A, ARTIFACT_B);
    LinkedHashSet<Artifact> resolvedArtifacts =
        new LinkedHashSet<>(ImmutableSet.of(ARTIFACT_A, ARTIFACT_B));
    Stage stage =
        new Stage(
            mock(Execution.class), "findArtifactFromExecution", getStageContext(expectedArtifacts));

    ArtifactUtils artifactUtils = mock(ArtifactUtils.class);
    when(artifactUtils.getArtifactsForPipelineId(
            eq(PIPELINE), any(ExecutionRepository.ExecutionCriteria.class)))
        .thenReturn(pipelineArtifacts);
    when(artifactUtils.resolveExpectedArtifacts(expectedArtifacts, pipelineArtifacts, null, false))
        .thenReturn(resolvedArtifacts);

    FindArtifactFromExecutionTask task = new FindArtifactFromExecutionTask(artifactUtils);
    TaskResult result = task.execute(stage);

    Collection<ExpectedArtifact> resolvedExpectedArtifacts =
        (Collection<ExpectedArtifact>) result.getContext().get("resolvedExpectedArtifacts");
    assertThat(resolvedExpectedArtifacts).containsExactly(EXPECTED_ARTIFACT_A, EXPECTED_ARTIFACT_B);

    Collection<Artifact> artifacts = (Collection<Artifact>) result.getContext().get("artifacts");
    assertThat(artifacts).containsExactly(ARTIFACT_A, ARTIFACT_B);
  }

  private static Map<String, Object> getStageContext(
      ImmutableList<ExpectedArtifact> expectedArtifacts) {
    Map<String, Object> executionOptions = new HashMap<>();
    executionOptions.put("succeeded", true);

    Map<String, Object> context = new HashMap<>();
    context.put("executionOptions", executionOptions);
    context.put("expectedArtifacts", expectedArtifacts);
    context.put("pipeline", PIPELINE);

    return context;
  }
}
