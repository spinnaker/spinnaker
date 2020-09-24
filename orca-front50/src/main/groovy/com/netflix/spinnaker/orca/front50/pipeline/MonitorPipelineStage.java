/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.front50.pipeline;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.netflix.spinnaker.kork.annotations.DeprecationInfo;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.front50.tasks.MonitorPipelineTask;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.tasks.artifacts.BindProducedArtifactsTask;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MonitorPipelineStage implements StageDefinitionBuilder {

  private final Logger log = LoggerFactory.getLogger(getClass());

  public static final String PIPELINE_CONFIG_TYPE =
      StageDefinitionBuilder.getType(MonitorPipelineStage.class);

  final ExecutionRepository executionRepository;

  @Autowired
  public MonitorPipelineStage(ExecutionRepository executionRepository) {
    this.executionRepository = executionRepository;
  }

  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    StageParameters stageData = stage.mapTo(StageParameters.class);

    builder.withTask("monitorPipeline", MonitorPipelineTask.class);

    if (stageData.expectedArtifacts != null) {
      builder.withTask(BindProducedArtifactsTask.TASK_NAME, BindProducedArtifactsTask.class);
    }
  }

  public enum MonitorBehavior {
    /**
     * During monitor, wait for all pipelines to complete before completing the stage even if a
     * monitored pipeline fails
     */
    WaitForAllToComplete,

    /** As soon as (at least) one monitored pipeline fails, terminate this stage */
    FailFast
  }

  public static class StageParameters {
    /**
     * DO NOT USE: Legacy from PipelineStage Use {@link executionIds} instead (even if you only have
     * one execution ID to monitor)
     */
    @Deprecated
    @DeprecationInfo(
        reason = "Used for backwards compat with PipelineStage",
        replaceWith = "Use executionIds instead even if you only have one execution ID",
        since = "2020.4",
        eol = "n/a")
    public String executionId;

    /** List of executions IDs to monitor */
    public List<String> executionIds;

    /** How to handle terminal pipelines */
    public MonitorBehavior monitorBehavior;

    /** List of expected artifacts */
    public List<ExpectedArtifact> expectedArtifacts;
  }

  public static class ChildPipelineException {
    public ChildPipelineExceptionSource source;
    public ChildPipelineExceptionDetails details;

    public ChildPipelineException() {
      source = new ChildPipelineExceptionSource();
      details = new ChildPipelineExceptionDetails();
    }

    public static class ChildPipelineExceptionDetails {
      public List<String> errors;
    }

    public static class ChildPipelineExceptionSource {
      public String executionId;
      public String stageId;
      public String stageName;
      public int stageIndex;
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ChildPipelineStatusDetails {
    public ExecutionStatus status;
    public String application;

    public ChildPipelineException exception;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class StageResult {
    public Map<String, ChildPipelineStatusDetails> executionStatuses;

    @JsonIgnore private final Map<String, Object> monitoredPipelineContext = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Object> pipelineContext() {
      return this.monitoredPipelineContext;
    }

    public void insertPipelineContext(Map<String, Object> pipelineContext) {
      monitoredPipelineContext.putAll(pipelineContext);
    }
  }
}
