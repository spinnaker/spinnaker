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
import com.netflix.spinnaker.orca.lock.RetriableLock;
import com.netflix.spinnaker.orca.lock.RetriableLock.RetriableLockOptions;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private final ExecutionRepository repository;
  private final ExecutionRunner runner;
  private final RetrySupport retrySupport;
  private final RetriableLock retriableLock;

  public void cancel(ExecutionType executionType, String executionId) {
    cancel(
        executionType,
        executionId,
        AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"),
        null);
  }

  public void cancel(ExecutionType executionType, String executionId, String user, String reason) {
    doInternalWithRetries(
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
    doInternalWithRetries(
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
    doInternalWithRetries(
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
    Runnable repositoryAction =
        () -> {
          PipelineExecution execution = repository.retrieve(executionType, executionId);
          StageExecution stage = execution.stageById(stageId);

          // mutates stage in place
          stageUpdater.accept(stage);

          repository.storeStage(stage);
        };
    return doInternalWithLocking(
        runner::reschedule, repositoryAction, "reschedule", executionType, executionId, stageId);
  }

  public PipelineExecution restartStage(@Nonnull String executionId, @Nonnull String stageId) {
    PipelineExecution execution = repository.retrieve(ExecutionType.PIPELINE, executionId);
    if (repository.handlesPartition(execution.getPartition())) {
      runner.restart(execution, stageId);
    } else {
      log.info(
          "Not pushing queue message action='restart' for execution with foreign partition='{}'",
          execution.getPartition());
      repository.restartStage(executionId, stageId);
    }
    return execution;
  }

  public PipelineExecution restartStage(
      @Nonnull String executionId, @Nonnull String stageId, Map restartDetails) {
    PipelineExecution execution = repository.retrieve(ExecutionType.PIPELINE, executionId);
    execution = updatePreconditionStageExpression(restartDetails, execution);
    if (repository.handlesPartition(execution.getPartition())) {
      runner.restart(execution, stageId);
    } else {
      log.info(
          "Not pushing queue message action='restart' for execution with foreign partition='{}'",
          execution.getPartition());
      repository.restartStage(executionId, stageId);
    }
    return execution;
  }

  private PipelineExecution updatePreconditionStageExpression(
      Map restartDetails, PipelineExecution execution) {
    List<Map> preconditionList = getPreconditionsFromStage(restartDetails);
    if (preconditionList.isEmpty()) {
      return execution;
    }

    for (StageExecution stage : execution.getStages()) {
      if (stage.getType() != null && stage.getType().equalsIgnoreCase("checkPreconditions")) {
        if (stage.getContext().get("preconditions") != null) {
          stage.getContext().replace("preconditions", preconditionList);
          repository.storeStage(stage);
          log.info("Updated preconditions for CheckPreconditions stage");
        }
      }
    }
    return execution;
  }

  private List<Map> getPreconditionsFromStage(Map restartDetails) {
    List<Map> preconditionList = new ArrayList();
    Map pipelineConfigMap = new HashMap(restartDetails);

    List<String> keysToRetain = new ArrayList();
    keysToRetain.add("stages");

    pipelineConfigMap.keySet().retainAll(keysToRetain);

    Map<String, List<Map>> pipelineStageMap = new HashMap(pipelineConfigMap);

    if (pipelineStageMap != null && !pipelineStageMap.isEmpty()) {
      List<Map> pipelineStageList = pipelineStageMap.get(keysToRetain.get(0));
      for (Map stageMap : pipelineStageList) {
        if (stageMap.get("type").toString().equalsIgnoreCase("checkPreconditions")) {
          preconditionList = (List<Map>) stageMap.get("preconditions");
          log.info("Retrieved preconditions for CheckPreconditions stage");
        }
      }
    }
    return preconditionList;
  }

  private PipelineExecution doInternal(
      Consumer<PipelineExecution> runnerAction,
      Runnable repositoryAction,
      String action,
      ExecutionType executionType,
      String executionId) {
    PipelineExecution toReturn = null;
    try {
      repositoryAction.run();

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

  private PipelineExecution doInternalWithLocking(
      Consumer<PipelineExecution> runnerAction,
      Runnable repositoryAction,
      String action,
      ExecutionType executionType,
      String executionId,
      String stageId) {
    var runnable = EnhancedExecution.withLocking(retriableLock, stageId, repositoryAction);
    return doInternal(runnerAction, runnable, action, executionType, executionId);
  }

  private PipelineExecution doInternalWithRetries(
      Consumer<PipelineExecution> runnerAction,
      Runnable repositoryAction,
      String action,
      ExecutionType executionType,
      String executionId) {
    var runnable = EnhancedExecution.withRetries(retrySupport, repositoryAction);
    return doInternal(runnerAction, runnable, action, executionType, executionId);
  }

  private <T> T runWithRetries(Supplier<T> action) {
    return retrySupport.retry(action, 5, Duration.ofMillis(100), false);
  }

  private static final class EnhancedExecution {

    static Runnable withLocking(RetriableLock lock, String lockName, Runnable action) {
      return () -> {
        var options = new RetriableLockOptions(lockName);
        var lockAcquired = lock.lock(options, action);
        if (!lockAcquired) {
          log.error("Failed to acquire lock {} in {} tries", lockName, options.getMaxRetries());
          throw new RuntimeException("Failed to acquire lock for key: " + lockName);
        }
      };
    }

    static Runnable withRetries(RetrySupport retrySupport, Runnable action) {
      Supplier<Boolean> actionSupplier =
          () -> {
            action.run();
            return true;
          };
      return () -> retrySupport.retry(actionSupplier, 5, Duration.ofMillis(100), false);
    }
  }
}
