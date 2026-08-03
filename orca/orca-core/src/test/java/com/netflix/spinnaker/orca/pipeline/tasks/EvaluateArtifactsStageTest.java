/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipeline.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.expressions.ExpressionEvaluationSummary;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.pipeline.EvaluateArtifactsStage;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class EvaluateArtifactsStageTest {

  private final ContextParameterProcessor contextParameterProcessor =
      new ContextParameterProcessor();
  private final EvaluateArtifactsStage evaluateArtifactsStage =
      new EvaluateArtifactsStage(new ObjectMapper());

  private StageExecution newStage(Map<String, Object> context) {
    return new StageExecutionImpl(
        new PipelineExecutionImpl(ExecutionType.PIPELINE, "test"), "evaluateArtifacts", context);
  }

  private Map<String, Object> artifactContent(String name, Object contents, String id) {
    Map<String, Object> artifactContent = new HashMap<>();
    artifactContent.put("name", name);
    artifactContent.put("contents", contents);
    artifactContent.put("id", id);
    return artifactContent;
  }

  @DisplayName("Should sequentially evaluate artifact contents")
  @Test
  public void shouldEvaluateArtifactContents() {
    ExpressionEvaluationSummary summary = new ExpressionEvaluationSummary();
    Map<String, Object> context = new HashMap<>();
    context.put(
        "artifactContents",
        List.of(
            artifactContent("artifactA", "${1+1}", "id-a"),
            artifactContent("artifactB", "${artifactA + 1}", "id-b")));
    StageExecution stage = newStage(context);

    boolean shouldContinue =
        evaluateArtifactsStage.processExpressions(stage, contextParameterProcessor, summary);

    assertThat(shouldContinue).isFalse();
    assertThat(getArtifactContents(stage)).hasSize(2);
    assertThat(getArtifactContent(stage, 0).getContents()).isEqualTo(2);
    assertThat(getArtifactContent(stage, 1).getContents()).isEqualTo(3);
    assertThat(summary.getFailureCount()).isZero();
  }

  @DisplayName("Should evaluate non-artifact part of context")
  @Test
  public void shouldEvaluateNonArtifactContext() {
    ExpressionEvaluationSummary summary = new ExpressionEvaluationSummary();
    Map<String, Object> context = new HashMap<>();
    context.put("notifications", List.of(Map.of("address", "${\"someone\" + \"@somewhere.com\"}")));
    StageExecution stage = newStage(context);

    boolean shouldContinue =
        evaluateArtifactsStage.processExpressions(stage, contextParameterProcessor, summary);

    assertThat(shouldContinue).isFalse();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> notifications =
        (List<Map<String, Object>>) stage.getContext().get("notifications");
    assertThat(notifications).hasSize(1);
    assertThat(notifications.get(0).get("address")).isEqualTo("someone@somewhere.com");
  }

  @DisplayName("Should skip artifact contents with missing expression")
  @Test
  public void shouldSkipArtifactContentsWithMissingExpression() {
    ExpressionEvaluationSummary summary = new ExpressionEvaluationSummary();
    Map<String, Object> context = new HashMap<>();
    context.put(
        "artifactContents",
        List.of(
            artifactContent("artifactA", "${1+1}", "id-a"),
            artifactContent("artifactB", "${a * c}", "id-b")));
    StageExecution stage = newStage(context);

    boolean shouldContinue =
        evaluateArtifactsStage.processExpressions(stage, contextParameterProcessor, summary);

    assertThat(shouldContinue).isFalse();
    assertThat(getArtifactContent(stage, 0).getContents()).isEqualTo(2);
    assertThat(getArtifactContent(stage, 1).getContents()).isEqualTo("${a * c}");
    assertThat(summary.getFailureCount()).isEqualTo(1);
  }

  @DisplayName("EvaluateArtifactsTask should produce embedded base64 artifacts")
  @Test
  public void shouldProduceEmbeddedBase64Artifacts() {
    EvaluateArtifactsTask task = new EvaluateArtifactsTask();
    Map<String, Object> context = new HashMap<>();
    context.put("artifactContents", List.of(artifactContent("artifactA", "hello world", "id-a")));
    StageExecution stage = newStage(context);

    TaskResult result = task.execute(stage);

    assertThat(result.getStatus().toString()).isEqualTo(ExecutionStatus.SUCCEEDED.toString());
    @SuppressWarnings("unchecked")
    List<Artifact> artifacts = (List<Artifact>) result.getOutputs().get("artifacts");
    assertThat(artifacts).hasSize(1);
    Artifact artifact = artifacts.get(0);
    assertThat(artifact.getName()).isEqualTo("artifactA");
    assertThat(artifact.getType()).isEqualTo("embedded/base64");
    assertThat(artifact.getArtifactAccount()).isEqualTo("embedded-artifact");
    assertThat(artifact.getReference())
        .isEqualTo(Base64.getEncoder().encodeToString("hello world".getBytes()));
  }

  @DisplayName("EvaluateArtifactsTask should skip artifact contents with empty contents")
  @Test
  public void shouldSkipEmptyArtifactContents() {
    EvaluateArtifactsTask task = new EvaluateArtifactsTask();
    Map<String, Object> context = new HashMap<>();
    List<Map<String, Object>> artifactContents = new ArrayList<>();
    artifactContents.add(artifactContent("artifactA", "hello", "id-a"));
    artifactContents.add(artifactContent("artifactB", "", "id-b"));
    artifactContents.add(artifactContent("artifactC", null, "id-c"));
    context.put("artifactContents", artifactContents);
    StageExecution stage = newStage(context);

    TaskResult result = task.execute(stage);

    assertThat(result.getStatus().toString()).isEqualTo(ExecutionStatus.SUCCEEDED.toString());
    @SuppressWarnings("unchecked")
    List<Artifact> artifacts = (List<Artifact>) result.getOutputs().get("artifacts");
    assertThat(artifacts).hasSize(1);
    assertThat(artifacts.get(0).getName()).isEqualTo("artifactA");
  }

  @SuppressWarnings("unchecked")
  private List<EvaluateArtifactsStage.ArtifactContent> getArtifactContents(StageExecution stage) {
    return (List<EvaluateArtifactsStage.ArtifactContent>)
        stage
            .mapTo(EvaluateArtifactsStage.EvaluateArtifactsStageContext.class)
            .getArtifactContents();
  }

  private EvaluateArtifactsStage.ArtifactContent getArtifactContent(
      StageExecution stage, int index) {
    return getArtifactContents(stage).get(index);
  }
}
