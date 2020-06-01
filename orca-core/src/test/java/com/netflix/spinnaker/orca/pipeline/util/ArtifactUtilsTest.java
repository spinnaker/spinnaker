/*
 * Copyright 2020 Google, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.pipeline.util;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.persistence.InMemoryExecutionRepository;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class ArtifactUtilsTest {
  @Test
  void withAccountNonNullAccount() {
    Artifact artifact = Artifact.builder().artifactAccount("old-account").build();
    Artifact newArtifact = ArtifactUtils.withAccount(artifact, "new-account");
    assertThat(newArtifact.getArtifactAccount()).isEqualTo("new-account");
  }

  @Test
  void withAccountDoesNotMutate() {
    Artifact artifact = Artifact.builder().artifactAccount("old-account").build();
    Artifact newArtifact = ArtifactUtils.withAccount(artifact, "new-account");
    assertThat(artifact.getArtifactAccount()).isEqualTo("old-account");
  }

  @Test
  void withAccountEmptyAccount() {
    Artifact artifact = Artifact.builder().artifactAccount("old-account").build();
    Artifact newArtifact = ArtifactUtils.withAccount(artifact, "");
    assertThat(newArtifact.getArtifactAccount()).isEqualTo("old-account");
  }

  @Test
  void withAccountNullAccount() {
    Artifact artifact = Artifact.builder().artifactAccount("old-account").build();
    Artifact newArtifact = ArtifactUtils.withAccount(artifact, null);
    assertThat(newArtifact.getArtifactAccount()).isEqualTo("old-account");
  }

  @Test
  void getBoundArtifactForIdPreservesAccount() {
    ArtifactUtils artifactUtils = createArtifactUtils();
    String id = "my-artifact-id";
    StageExecution execution =
        executionWithArtifacts(
            ImmutableList.of(
                ExpectedArtifact.builder()
                    .id(id)
                    .matchArtifact(Artifact.builder().artifactAccount("match-account").build())
                    .boundArtifact(Artifact.builder().artifactAccount("bound-account").build())
                    .build()));
    Artifact result = artifactUtils.getBoundArtifactForId(execution, id);
    assertThat(result.getArtifactAccount()).isEqualTo("bound-account");
  }

  @Test
  void getBoundArtifactForIdDefaultsToMatchAccount() {
    ArtifactUtils artifactUtils = createArtifactUtils();
    String id = "my-artifact-id";
    StageExecution execution =
        executionWithArtifacts(
            ImmutableList.of(
                ExpectedArtifact.builder()
                    .id(id)
                    .matchArtifact(Artifact.builder().artifactAccount("match-account").build())
                    .boundArtifact(Artifact.builder().build())
                    .build()));
    Artifact result = artifactUtils.getBoundArtifactForId(execution, id);
    assertThat(result.getArtifactAccount()).isEqualTo("match-account");
  }

  @Test
  void getBoundArtifactForIdReturnsNull() {
    ArtifactUtils artifactUtils = createArtifactUtils();
    String id = "my-artifact-id";
    StageExecution execution = executionWithArtifacts(ImmutableList.of());
    Artifact result = artifactUtils.getBoundArtifactForId(execution, id);
    assertThat(result).isNull();
  }

  private StageExecution executionWithArtifacts(Iterable<ExpectedArtifact> expectedArtifacts) {
    Map<String, Object> context = new HashMap<>();
    context.put("resolvedExpectedArtifacts", ImmutableList.copyOf(expectedArtifacts));
    return createExecution(context);
  }

  private StageExecution createExecution(Map<String, Object> context) {
    PipelineExecution pipeline = new PipelineExecutionImpl(PIPELINE, "3", "foo");
    return new StageExecutionImpl(pipeline, "test", context);
  }

  private ArtifactUtils createArtifactUtils() {
    return new ArtifactUtils(
        new ObjectMapper(), new InMemoryExecutionRepository(), new ContextParameterProcessor());
  }
}
