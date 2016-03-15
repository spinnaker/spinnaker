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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ExecutionLauncher<T extends Execution> {

  protected final ObjectMapper objectMapper;
  protected final String currentInstanceId;
  protected final ExecutionRepository executionRepository;

  private final ExecutionRunner runner;

  protected ExecutionLauncher(ObjectMapper objectMapper,
                              String currentInstanceId,
                              ExecutionRepository executionRepository,
                              ExecutionRunner runner) {
    this.objectMapper = objectMapper;
    this.currentInstanceId = currentInstanceId;
    this.executionRepository = executionRepository;
    this.runner = runner;
  }

  public T start(String configJson) throws Exception {
    final T execution = parse(configJson);

    persistExecution(execution);

    if (shouldQueue(execution)) {
      log.info("Queueing {}", execution.getId());
    } else {
      runner.start(execution);
    }
    return execution;
  }

  protected abstract T parse(String configJson) throws IOException;

  /**
   * Persist the initial execution configuration.
   */
  protected abstract void persistExecution(T execution);

  /**
   * Hook for subclasses to decide if this execution should be queued or start immediately.
   *
   * @return true if the stage should be queued.
   */
  protected boolean shouldQueue(T execution) {
    return false;
  }

}
