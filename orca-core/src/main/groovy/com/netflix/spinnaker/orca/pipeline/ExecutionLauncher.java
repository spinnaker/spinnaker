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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Orchestration;
import com.netflix.spinnaker.orca.pipeline.model.Pipeline;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.lang.Boolean.parseBoolean;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

public abstract class ExecutionLauncher<T extends Execution<T>> {

  private final Logger log = LoggerFactory.getLogger(getClass());

  protected final ObjectMapper objectMapper;
  protected final ExecutionRepository executionRepository;

  private final ExecutionRunner executionRunner;

  protected ExecutionLauncher(ObjectMapper objectMapper,
                              ExecutionRepository executionRepository,
                              ExecutionRunner executionRunner) {
    this.objectMapper = objectMapper;
    this.executionRepository = executionRepository;
    this.executionRunner = executionRunner;
  }

  public T start(String configJson) throws Exception {
    final T execution = parse(configJson);

    final T existingExecution = checkForCorrelatedExecution(execution);
    if (existingExecution != null) {
      return existingExecution;
    }

    checkRunnable(execution);

    persistExecution(execution);

    try {
      start(execution);
    } catch (Throwable t) {
      handleStartupFailure(execution, t);
    }

    return execution;
  }

  protected void checkRunnable(T execution) {
    // no-op by default
  }

  public T start(T execution) throws Exception {
    if (shouldQueue(execution)) {
      log.info("Queueing {}", execution.getId());
    } else {
      executionRunner.start(execution);
      onExecutionStarted(execution);
    }
    return execution;
  }

  protected T checkForCorrelatedExecution(T execution) {
    // Correlated executions currently only supported by Orchestrations. Just lazy, and a carrot to
    // refactoring out distinction between Pipeline / Orchestration.
    return null;
  }

  protected T handleStartupFailure(T execution, Throwable failure) {
    final String canceledBy = "system";
    final String reason = "Failed on startup: " + failure.getMessage();
    final ExecutionStatus status = ExecutionStatus.TERMINAL;
    final Function<Execution, Execution> reloader;
    final String executionType;
    if (execution instanceof Pipeline) {
      executionType = "pipeline";
      reloader = (e) -> executionRepository.retrievePipeline(e.getId());
    } else if (execution instanceof Orchestration) {
      executionType = "orchestration";
      reloader = (e) -> executionRepository.retrieveOrchestration(e.getId());
    } else {
      //This should really never happen. If it does, git-blame whoever added the third
      // type of Execution and yell at them...
      log.error("Unknown execution type: " + execution.getClass().getSimpleName());
      executionType = "unknown";
      reloader = (e) -> {
        e.setCancellationReason(reason);
        e.setCanceled(true);
        e.setCanceledBy(canceledBy);
        e.setStatus(status);
        return e;
      };
    }

    log.error("Failed to start " + executionType + " " + execution.getId(), failure);
    executionRepository.updateStatus(execution.getId(), status);
    executionRepository.cancel(execution.getId(), canceledBy, reason);
    return (T) reloader.apply(execution);
  }

  protected void onExecutionStarted(T execution) {
  }

  protected abstract T parse(String configJson) throws IOException;

  /**
   * Persist the initial execution configuration.
   */
  protected abstract void persistExecution(T execution);

  protected final boolean getBoolean(Map<String, ?> map, String key) {
    return parseBoolean(getString(map, key));
  }

  protected final String getString(Map<String, ?> map, String key) {
    return map.containsKey(key) ? map.get(key).toString() : null;
  }

  protected final <K, V> Map<K, V> getMap(Map<String, ?> map, String key) {
    Map<K, V> result = (Map<K, V>) map.get(key);
    return result == null ? emptyMap() : result;
  }

  protected final List<Map<String, Object>> getList(Map<String, ?> map, String key) {
    List<Map<String, Object>> result = (List<Map<String, Object>>) map.get(key);
    return result == null ? emptyList() : result;
  }

  protected final <E extends Enum<E>> E getEnum(Map<String, ?> map, String key, Class<E> type) {
    String value = (String) map.get(key);
    return value != null ? Enum.valueOf(type, value) : null;
  }

  /**
   * Hook for subclasses to decide if this execution should be queued or start immediately.
   *
   * @return true if the stage should be queued.
   */
  protected boolean shouldQueue(T execution) {
    return false;
  }

}
