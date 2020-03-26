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
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Value
@NonFinal
public class CompoundExecutionOperator {
  ExecutionRepository repository;
  ExecutionRunner runner;
  RetrySupport retrySupport;

  public void cancel(ExecutionType executionType, String executionId) {
    cancel(
        executionType,
        executionId,
        AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"),
        null);
  }

  public void cancel(ExecutionType executionType, String executionId, String user, String reason) {
    doInternal(
        (PipelineExecution execution) -> runner.cancel(execution, user, reason),
        () -> repository.cancel(executionType, executionId, user, reason),
        "cancel",
        executionType,
        executionId);
  }

  public void delete(ExecutionType executionType, String executionId) {
    repository.delete(executionType, executionId);
  }

  public void pause(
      @Nonnull ExecutionType executionType,
      @Nonnull String executionId,
      @Nullable String pausedBy) {
    doInternal(
        runner::reschedule,
        () -> repository.pause(executionType, executionId, pausedBy),
        "pause",
        executionType,
        executionId);
  }

  public void resume(
      @Nonnull ExecutionType executionType,
      @Nonnull String executionId,
      @Nullable String user,
      @Nonnull Boolean ignoreCurrentStatus) {
    doInternal(
        runner::unpause,
        () -> repository.resume(executionType, executionId, user, ignoreCurrentStatus),
        "resume",
        executionType,
        executionId);
  }

  public PipelineExecution updateStage(
      @Nonnull ExecutionType executionType,
      @Nonnull String executionId,
      @Nonnull String stageId,
      @Nonnull Consumer<StageExecution> stageUpdater) {
    return doInternal(
        runner::reschedule,
        () -> {
          PipelineExecution execution = repository.retrieve(executionType, executionId);
          StageExecution stage = execution.stageById(stageId);

          // mutates stage in place
          stageUpdater.accept(stage);

          repository.storeStage(stage);
        },
        "reschedule",
        executionType,
        executionId);
  }

  private PipelineExecution doInternal(
      Consumer<PipelineExecution> runnerAction,
      Runnable repositoryAction,
      String action,
      ExecutionType executionType,
      String executionId) {
    PipelineExecution toReturn = null;
    try {
      runWithRetries(repositoryAction);

      toReturn =
          runWithRetries(
              () -> {
                PipelineExecution execution = repository.retrieve(executionType, executionId);
                if (repository.handlesPartition(execution.getPartition())) {
                  runnerAction.accept(execution);
                } else {
                  log.info(
                      "Not pushing queue message action='{}' for execution with foreign partition='{}'",
                      action,
                      execution.getPartition());
                }
                return execution;
              });
    } catch (Exception e) {
      log.error(
          "Failed to {} execution with executionType={} and executionId={}",
          action,
          executionType,
          executionId,
          e);
    }
    return toReturn;
  }

  private <T> T runWithRetries(Supplier<T> action) {
    return retrySupport.retry(action, 5, Duration.ofMillis(100), false);
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
