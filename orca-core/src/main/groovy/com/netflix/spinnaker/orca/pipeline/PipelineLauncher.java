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
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Pipeline;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static java.lang.Boolean.parseBoolean;

@Component
public class PipelineLauncher extends ExecutionLauncher<Pipeline> {

  private final Optional<PipelineStartTracker> startTracker;

  @Autowired
  public PipelineLauncher(ObjectMapper objectMapper,
                          String currentInstanceId,
                          ExecutionRepository executionRepository,
                          ExecutionRunner runner,
                          Optional<PipelineStartTracker> startTracker) {
    super(objectMapper, currentInstanceId, executionRepository, runner);
    this.startTracker = startTracker;
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
      .withAppConfig((Map<String, Serializable>) config.get("appConfig"))
      .withParallel(getBoolean(config, "parallel"))
      .withLimitConcurrent(getBoolean(config, "limitConcurrent"))
      .withKeepWaitingPipelines(getBoolean(config, "keepWaitingPipelines"))
      .withExecutingInstance(currentInstanceId)
      .withExecutionEngine(Execution.V2_EXECUTION_ENGINE)
      .withNotifications((List<Map<String, Object>>) config.get("notifications"))
      .withId()
      .build();
  }

  @Override
  protected void persistExecution(Pipeline execution) {
    executionRepository.store(execution);
  }

  private boolean getBoolean(Map<String, ?> map, String key) {
    return parseBoolean(getString(map, key));
  }

  private String getString(Map<String, ?> map, String key) {
    return map.containsKey(key) ? map.get(key).toString() : null;
  }

  @Override protected boolean shouldQueue(Pipeline execution) {
    if (execution.getPipelineConfigId() == null || !execution.getLimitConcurrent()) {
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
}
