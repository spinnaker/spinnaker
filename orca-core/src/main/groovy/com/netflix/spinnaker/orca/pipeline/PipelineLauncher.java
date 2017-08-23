/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionEngine;
import com.netflix.spinnaker.orca.pipeline.model.Pipeline;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PipelineLauncher extends ExecutionLauncher<Pipeline> {

  private final Optional<PipelineStartTracker> startTracker;

  private final Optional<PipelineValidator> pipelineValidator;

  @Autowired
  public PipelineLauncher(ObjectMapper objectMapper,
                          ExecutionRepository executionRepository,
                          ExecutionRunner executionRunner,
                          Optional<PipelineStartTracker> startTracker,
                          Optional<PipelineValidator> pipelineValidator) {
    super(objectMapper, executionRepository, executionRunner);
    this.startTracker = startTracker;
    this.pipelineValidator = pipelineValidator;
  }

  @SuppressWarnings("unchecked")
  @Override protected Pipeline parse(String configJson) throws IOException {
    // TODO: can we not just annotate the class properly to avoid all this?
    Map<String, Serializable> config = objectMapper.readValue(configJson, Map.class);
    return Pipeline
      .builder()
      .withApplication(getString(config, "application"))
      .withName(getString(config, "name"))
      .withPipelineConfigId(getString(config, "id"))
      .withTrigger((Map<String, Object>) config.get("trigger"))
      .withStages((List<Map<String, Object>>) config.get("stages"))
      .withParallel(getBoolean(config, "parallel"))
      .withLimitConcurrent(getBoolean(config, "limitConcurrent"))
      .withKeepWaitingPipelines(getBoolean(config, "keepWaitingPipelines"))
      .withNotifications((List<Map<String, Object>>) config.get("notifications"))
      .withExecutionEngine(getEnum(config, "executionEngine", ExecutionEngine.class))
      .withOrigin(getString(config, "origin"))
      .build();
  }

  @Override
  protected void persistExecution(Pipeline execution) {
    executionRepository.store(execution);
  }

  @Override protected boolean shouldQueue(Pipeline execution) {
    if (execution.getPipelineConfigId() == null || !execution.isLimitConcurrent()) {
      return false;
    }
    return startTracker
      .map(tracker ->
        tracker.queueIfNotStarted(execution.getPipelineConfigId(), execution.getId()))
      .orElse(false);
  }

  @Override protected void onExecutionStarted(Pipeline execution) {
    startTracker
      .ifPresent(tracker -> {
        tracker.addToStarted(execution.getPipelineConfigId(), execution.getId());
      });
  }

  @Override protected void checkRunnable(Pipeline execution) {
    pipelineValidator.ifPresent(it -> it.checkRunnable(execution));
  }

  @Override
  protected Pipeline handleStartupFailure(Pipeline execution, Throwable failure) {
    Pipeline failed = super.handleStartupFailure(execution, failure);
    startTracker.ifPresent(tracker -> {
      if (execution.getPipelineConfigId() != null) {
        tracker.removeFromQueue(execution.getPipelineConfigId(), execution.getId());
      }
      tracker.markAsFinished(execution.getPipelineConfigId(), execution.getId());
    });
    return failed;
  }
}
