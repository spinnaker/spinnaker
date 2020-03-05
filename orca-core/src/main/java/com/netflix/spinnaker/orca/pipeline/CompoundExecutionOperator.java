/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline;

import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.time.Duration;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompoundExecutionOperator {
  private ExecutionRepository repository;
  private ExecutionRunner runner;
  private RetrySupport retrySupport;

  public CompoundExecutionOperator(
      ExecutionRepository repository, ExecutionRunner runner, RetrySupport retrySupport) {
    this.repository = repository;
    this.runner = runner;
    this.retrySupport = retrySupport;
  }

  public void cancel(ExecutionType executionType, String executionId) {
    cancel(
        executionType,
        executionId,
        AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"),
        null);
  }

  public void cancel(ExecutionType executionType, String executionId, String user, String reason) {
    doInternal(
        (Execution execution) -> runner.cancel(execution, user, reason),
        () -> repository.cancel(executionType, executionId, user, reason),
        "cancel",
        executionType,
        executionId);
  }

  public void delete(ExecutionType executionType, String executionId) {
    repository.delete(executionType, executionId);
  }

  public void pause(
      @NonNull ExecutionType executionType,
      @NonNull String executionId,
      @Nullable String pausedBy) {
    doInternal(
        (Execution execution) -> runner.reschedule(execution),
        () -> repository.pause(executionType, executionId, pausedBy),
        "pause",
        executionType,
        executionId);
  }

  public void resume(
      @NonNull ExecutionType executionType,
      @NonNull String executionId,
      @Nullable String user,
      @NonNull Boolean ignoreCurrentStatus) {
    doInternal(
        (Execution execution) -> runner.unpause(execution),
        () -> repository.resume(executionType, executionId, user, ignoreCurrentStatus),
        "resume",
        executionType,
        executionId);
  }

  private void doInternal(
      Consumer<Execution> runnerAction,
      Runnable repositoryAction,
      String action,
      ExecutionType executionType,
      String executionId) {
    try {
      runWithRetries(
          () -> {
            Execution execution = repository.retrieve(executionType, executionId);
            if (repository.handlesPartition(execution.getPartition())) {
              runnerAction.accept(execution);
            } else {
              log.info(
                  "Not pushing queue message action='{}' for execution with foreign partition='{}'",
                  action,
                  execution.getPartition());
            }
          });
      runWithRetries(repositoryAction);
    } catch (Exception e) {
      log.error(
          "Failed to {} execution with executionType={} and executionId={}",
          action,
          executionType,
          executionId,
          e);
    }
  }

  private void runWithRetries(Runnable action) {
    retrySupport.retry(
        () -> {
          action.run();
          return true;
        },
        5,
        Duration.ofMillis(100),
        false);
  }
}
