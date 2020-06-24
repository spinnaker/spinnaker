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
 *
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.appengine;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

final class AppEngineServerGroupCreatorTest {
  private static final ObjectMapper OBJECT_MAPPER = OrcaObjectMapper.getInstance();

  @Test
  void appendArtifactDataArtifact() {
    AppEngineServerGroupCreator appEngineServerGroupCreator = new AppEngineServerGroupCreator();
    appEngineServerGroupCreator.setObjectMapper(OBJECT_MAPPER);
    appEngineServerGroupCreator.setArtifactUtils(mockArtifactUtils());

    StageExecution stage = createExecution();
    Map<String, Object> operation = new HashMap<>();
    operation.put(
        "configArtifacts",
        ImmutableList.of(artifactAccountPair("my-id", "my-account", Artifact.builder().build())));

    appEngineServerGroupCreator.appendArtifactData(stage, operation);

    List<Artifact> configArtifacts = (List<Artifact>) operation.get("configArtifacts");
    assertThat(configArtifacts).hasSize(1);

    Artifact singleArtifact = Iterables.getOnlyElement(configArtifacts);
    assertThat(singleArtifact.getArtifactAccount()).isEqualTo("my-account");
  }

  @Test
  void appendArtifactDataMap() {
    AppEngineServerGroupCreator appEngineServerGroupCreator = new AppEngineServerGroupCreator();
    appEngineServerGroupCreator.setObjectMapper(OBJECT_MAPPER);
    appEngineServerGroupCreator.setArtifactUtils(mockArtifactUtils());

    StageExecution stage = createExecution();
    Map<String, Object> operation = new HashMap<>();
    operation.put(
        "configArtifacts",
        ImmutableList.of(
            ImmutableMap.of(
                "id", "my-id",
                "account", "my-account",
                "artifact", ImmutableMap.of())));

    appEngineServerGroupCreator.appendArtifactData(stage, operation);

    List<Artifact> configArtifacts = (List<Artifact>) operation.get("configArtifacts");
    assertThat(configArtifacts).hasSize(1);

    Artifact singleArtifact = Iterables.getOnlyElement(configArtifacts);
    assertThat(singleArtifact.getArtifactAccount()).isEqualTo("my-account");
  }

  @Test
  void doesNotOvewriteNullAccount() {
    AppEngineServerGroupCreator appEngineServerGroupCreator = new AppEngineServerGroupCreator();
    appEngineServerGroupCreator.setObjectMapper(OBJECT_MAPPER);
    appEngineServerGroupCreator.setArtifactUtils(mockArtifactUtils());

    StageExecution stage = createExecution();
    Map<String, Object> operation = new HashMap<>();
    operation.put(
        "configArtifacts",
        ImmutableList.of(
            artifactAccountPair(
                "my-id", null, Artifact.builder().artifactAccount("my-account").build())));

    appEngineServerGroupCreator.appendArtifactData(stage, operation);

    List<Artifact> configArtifacts = (List<Artifact>) operation.get("configArtifacts");
    assertThat(configArtifacts).hasSize(1);

    Artifact singleArtifact = Iterables.getOnlyElement(configArtifacts);
    assertThat(singleArtifact.getArtifactAccount()).isEqualTo("my-account");
  }

  private ArtifactUtils mockArtifactUtils() {
    ArtifactUtils artifactUtils = mock(ArtifactUtils.class);

    // Just return the input artifact when looking for a bound artifact.
    when(artifactUtils.getBoundArtifactForStage(
            any(StageExecution.class), any(String.class), any(Artifact.class)))
        .thenAnswer((Answer<Artifact>) invocation -> (Artifact) invocation.getArguments()[2]);

    return artifactUtils;
  }

  private StageExecution createExecution() {
    PipelineExecution pipeline = new PipelineExecutionImpl(PIPELINE, "3", "foo");
    return new StageExecutionImpl(pipeline, "test", new HashMap<>());
  }

  private ArtifactAccountPair artifactAccountPair(String id, String account, Artifact artifact) {
    ArtifactAccountPair result = new ArtifactAccountPair();
    result.setId(id);
    result.setAccount(account);
    result.setArtifact(artifact);
    return result;
  }
}
