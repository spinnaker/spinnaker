/*
 * Copyright 2024 Harness, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.tasks.job.DestroyJobTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.kubernetes.Manifest;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilderImpl;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RunJobStageTest {
  private RunJobStage runJobStage;

  private DestroyJobTask destroyJobTask;

  private static final String APPLICATION = "my-app";

  private static final String ACCOUNT = "my-acct";

  private static final String RUNJOB_STAGE_TYPE = "runJobManifest";

  private static final ObjectMapper objectMapper = OrcaObjectMapper.getInstance();

  @BeforeEach
  void setUp() {
    runJobStage = new RunJobStage(destroyJobTask, null);
  }

  @Test
  void testManifestArtifactWithLogAnnotation() {
    RunJobStageContext runJobStageContext = new RunJobStageContext();
    Map<String, Object> context =
        objectMapper.convertValue(runJobStageContext, new TypeReference<Map<String, Object>>() {});
    context.put("account", ACCOUNT);
    context.put("type", RUNJOB_STAGE_TYPE);
    context.put("status", ExecutionStatus.SUCCEEDED);
    context.put(
        "manifestArtifact",
        ImmutableMap.of("artifactAccount", "github", "reference", "my-artifact"));

    Manifest jobManifest = new Manifest();
    jobManifest.metadata.setAnnotations(Map.of("job.spinnaker.io/logs", "https://example.com/"));
    context.put("outputs.manifests", List.of(jobManifest));
    StageExecutionImpl stage =
        new StageExecutionImpl(
            new PipelineExecutionImpl(ExecutionType.PIPELINE, APPLICATION),
            RUNJOB_STAGE_TYPE,
            context);
    stage.setOutputs(context);
    getAfterStages(stage);

    assertThat(stage.getContext()).extracting(s -> s.get("manifestArtifact")).isNotNull();
    assertThat(stage.getContext()).extracting(s -> s.get("outputs.manifests")).isNotNull();
    assertThat(stage.getContext())
        .extracting(s -> s.get("execution"))
        .isEqualTo(ImmutableMap.of("logs", "https://example.com/"));
    assertThat(stage.getOutputs()).isNotEmpty();
  }

  @Test
  void testManifestArtifactWithOutLogAnnotation() {
    RunJobStageContext runJobStageContext = new RunJobStageContext();
    Map<String, Object> context =
        objectMapper.convertValue(runJobStageContext, new TypeReference<Map<String, Object>>() {});
    context.put("account", ACCOUNT);
    context.put("type", RUNJOB_STAGE_TYPE);
    context.put("status", ExecutionStatus.SUCCEEDED);
    context.put(
        "manifestArtifact",
        ImmutableMap.of("artifactAccount", "github", "reference", "my-artifact"));

    Manifest jobManifest = new Manifest();
    jobManifest.metadata.setAnnotations(Map.of("app", "name"));
    context.put("outputs.manifests", List.of(jobManifest));
    StageExecutionImpl stage =
        new StageExecutionImpl(
            new PipelineExecutionImpl(ExecutionType.PIPELINE, APPLICATION),
            RUNJOB_STAGE_TYPE,
            context);
    stage.setOutputs(context);
    getAfterStages(stage);

    assertThat(stage.getContext()).extracting(s -> s.get("manifestArtifact")).isNotNull();
    assertThat(stage.getContext()).extracting(s -> s.get("outputs.manifests")).isNotNull();
    assertThat(stage.getContext())
        .doesNotContain(entry("execution", ImmutableMap.of("logs", "https://example.com/")));
    assertThat(stage.getOutputs()).isNotEmpty();
  }

  @Test
  void testManifestArtifactLogAnnotationANDnoOutput() {
    RunJobStageContext runJobStageContext = new RunJobStageContext();
    Map<String, Object> context =
        objectMapper.convertValue(runJobStageContext, new TypeReference<Map<String, Object>>() {});
    context.put("account", ACCOUNT);
    context.put("type", RUNJOB_STAGE_TYPE);
    context.put("status", ExecutionStatus.SUCCEEDED);
    context.put(
        "manifestArtifact",
        ImmutableMap.of("artifactAccount", "github", "reference", "my-artifact"));
    context.put("noOutput", true);

    Manifest jobManifest = new Manifest();
    jobManifest.metadata.setAnnotations(Map.of("job.spinnaker.io/logs", "https://example.com/"));
    context.put("outputs.manifests", List.of(jobManifest));
    StageExecutionImpl stage =
        new StageExecutionImpl(
            new PipelineExecutionImpl(ExecutionType.PIPELINE, APPLICATION),
            RUNJOB_STAGE_TYPE,
            context);
    stage.setOutputs(context);
    getAfterStages(stage);

    assertThat(stage.getContext()).extracting(s -> s.get("manifestArtifact")).isNotNull();
    assertThat(stage.getContext()).extracting(s -> s.get("outputs.manifests")).isNotNull();
    assertThat(stage.getContext())
        .extracting(s -> s.get("execution"))
        .isEqualTo(ImmutableMap.of("logs", "https://example.com/"));
    assertThat(stage.getOutputs()).isEmpty();
  }

  private Iterable<StageExecution> getAfterStages(StageExecutionImpl stage) {
    StageGraphBuilderImpl graph = StageGraphBuilderImpl.afterStages(stage);
    runJobStage.afterStages(stage, graph);
    return graph.build();
  }
}
