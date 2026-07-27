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

package com.netflix.spinnaker.orca.pipeline;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.expressions.ExpressionEvaluationSummary;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator;
import com.netflix.spinnaker.orca.pipeline.model.StageContext;
import com.netflix.spinnaker.orca.pipeline.tasks.EvaluateArtifactsTask;
import com.netflix.spinnaker.orca.pipeline.tasks.artifacts.BindProducedArtifactsTask;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import java.util.*;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EvaluateArtifactsStage extends ExpressionAwareStageDefinitionBuilder {

  public static String STAGE_TYPE = "evaluateArtifacts";

  private final ObjectMapper mapper;

  @Autowired
  public EvaluateArtifactsStage(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    builder
        .withTask("evaluateArtifacts", EvaluateArtifactsTask.class)
        .withTask("bindArtifacts", BindProducedArtifactsTask.class);
  }

  @Override
  public boolean processExpressions(
      @Nonnull StageExecution stage,
      @Nonnull ContextParameterProcessor contextParameterProcessor,
      @Nonnull ExpressionEvaluationSummary summary) {

    processDefaultEntries(
        stage, contextParameterProcessor, summary, Collections.singletonList("artifactContents"));

    EvaluateArtifactsStageContext context = stage.mapTo(EvaluateArtifactsStageContext.class);

    StageContext augmentedContext = contextParameterProcessor.buildExecutionContext(stage);

    Map<String, Object> varSourceToEval = new HashMap<>();
    int lastFailedCount = 0;

    List<ArtifactContent> artifactContents =
        Optional.ofNullable(context.getArtifactContents()).orElse(Collections.emptyList());

    for (ArtifactContent artifactContent : artifactContents) {
      if (artifactContent.getContents() instanceof String) {
        varSourceToEval.put("contents", artifactContent.getContents());

        Map<String, Object> evaluatedContents =
            contextParameterProcessor.process(varSourceToEval, augmentedContext, true, summary);

        boolean evaluationSucceeded = summary.getFailureCount() == lastFailedCount;
        if (evaluationSucceeded) {
          artifactContent.setContents(evaluatedContents.get("contents"));
          augmentedContext.put(artifactContent.getName(), artifactContent.getContents());
        } else {
          lastFailedCount = summary.getFailureCount();
        }
      }
    }

    Map<String, Object> evaluatedContext =
        mapper.convertValue(context, new TypeReference<Map<String, Object>>() {});
    stage.getContext().putAll(evaluatedContext);

    if (summary.getFailureCount() > 0) {
      stage
          .getContext()
          .put(
              PipelineExpressionEvaluator.SUMMARY,
              mapper.convertValue(summary.getExpressionResult(), Map.class));
    }

    return false;
  }

  public static final class EvaluateArtifactsStageContext {
    private final List<ArtifactContent> artifactContents;

    @JsonCreator
    public EvaluateArtifactsStageContext(
        @JsonProperty("artifactContents") @Nullable List<ArtifactContent> artifactContents) {
      this.artifactContents = artifactContents;
    }

    public @Nullable List<ArtifactContent> getArtifactContents() {
      return artifactContents;
    }
  }

  public static class ArtifactContent {
    /** Variable name: NOT processed by SpEL */
    private String name;

    /** Variable evaluated value (processed by SpEL) */
    private Object contents;

    private String id;

    public ArtifactContent() {}

    public void setName(String name) {
      this.name = name;
    }

    public void setContents(Object contents) {
      this.contents = contents;
    }

    public String getName() {
      return name;
    }

    public Object getContents() {
      return contents;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }
  }
}
