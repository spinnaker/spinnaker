/*
 * Copyright (c) 2019 Schibsted Media Group.
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
package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.cloudformation;

import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WaitForCloudFormationCompletionTask implements OverridableTimeoutRetryableTask {

  public static final String TASK_NAME = "waitForCloudFormationCompletion";
  private final long backoffPeriod = TimeUnit.SECONDS.toMillis(10);
  private final long timeout = TimeUnit.HOURS.toMillis(2);
  @Autowired private OortService oortService;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    try {
      Map task = ((List<Map>) stage.getContext().get("kato.tasks")).iterator().next();
      Map result = ((List<Map>) task.get("resultObjects")).iterator().next();
      String stackId = (String) result.get("stackId");
      Map<String, ?> stack =
          (Map<String, Object>)
              Retrofit2SyncCall.execute(oortService.getCloudFormationStack(stackId));
      log.info(
          "Received cloud formation stackId "
              + stackId
              + " with status "
              + stack.get("stackStatus"));

      boolean isChangeSet = isChangeSetStage(stage);
      boolean isChangeSetExecution = isChangeSetExecution(stage);
      boolean isChangeSetDeletion = isChangeSetDeletion(stage);

      log.info("Deploying a CloudFormation ChangeSet for stackId " + stackId + ": " + isChangeSet);

      String status =
          (isChangeSet && !isChangeSetExecution && !isChangeSetDeletion)
              ? getChangeSetInfo(stack, stage.getContext(), "status")
              : getStackInfo(stack, "stackStatus");

      if (isComplete(status)) {
        return TaskResult.builder(ExecutionStatus.SUCCEEDED).outputs(stack).build();
      }
      if (isEmptyChangeSet(stage, stack)) {
        String changeSetName = (String) result.get("changeSetName");
        log.info("CloudFormation ChangeSet {} empty. Requesting to be deleted.", changeSetName);
        return TaskResult.builder(ExecutionStatus.SUCCEEDED)
            .context("deleteChangeSet", true)
            .outputs(stack)
            .build();
      }
      if (isInProgress(status)) {
        return TaskResult.RUNNING;
      }
      if (isFailed(status)) {
        String statusReason = getFailureReason(stack, stage);
        log.info("Cloud formation stack failed to completed. Status: " + statusReason);
        throw new RuntimeException(statusReason);
      }
      throw new RuntimeException("Unexpected stack status: " + stack.get("stackStatus"));
    } catch (SpinnakerHttpException e) {
      if (e.getResponseCode() == HttpStatus.NOT_FOUND.value()) {
        // The cache might not be up to date, try in the next iteration.
        return TaskResult.RUNNING;
      } else {
        log.error("Error retrieving cloud formation stack", e);
        throw new RuntimeException(e.getMessage());
      }
    }
  }

  private boolean isChangeSetStage(StageExecution stage) {
    return (boolean) Optional.ofNullable(stage.getContext().get("isChangeSet")).orElse(false);
  }

  private boolean isChangeSetExecution(StageExecution stage) {
    return (boolean)
        Optional.ofNullable(stage.getContext().get("isChangeSetExecution")).orElse(false);
  }

  private boolean isChangeSetDeletion(StageExecution stage) {
    return (boolean) Optional.ofNullable(stage.getContext().get("deleteChangeSet")).orElse(false);
  }

  @Override
  public long getBackoffPeriod() {
    return backoffPeriod;
  }

  @Override
  public long getTimeout() {
    return timeout;
  }

  private String getFailureReason(Map stack, StageExecution stage) {
    List unrecoverableStatuses =
        Arrays.asList(
            CloudFormationStates.ROLLBACK_COMPLETE.name(),
            CloudFormationStates.ROLLBACK_FAILED.name(),
            CloudFormationStates.UPDATE_ROLLBACK_FAILED.name());
    if (unrecoverableStatuses.contains(stack.get("stackStatus"))) {
      return "Irrecoverable stack status - Review the error, make changes in template and delete the stack to re-run the pipeline successfully; Reason: "
          + getStackInfo(stack, "stackStatusReason");
    }

    if (isChangeSetStage(stage) && !isChangeSetExecution(stage) && !isChangeSetDeletion(stage)) {
      return getChangeSetInfo(stack, stage.getContext(), "statusReason");
    } else {
      return getStackInfo(stack, "stackStatusReason");
    }
  }

  private String getStackInfo(Map stack, String field) {
    return (String) stack.get(field);
  }

  private String getChangeSetInfo(Map stack, Map context, String field) {
    String changeSetName = (String) context.get("changeSetName");
    log.debug("Getting change set status from stack for changeset {}: {}", changeSetName, stack);
    return Optional.ofNullable((List<Map<String, ?>>) stack.get("changeSets"))
        .orElse(Collections.emptyList())
        .stream()
        .filter(changeSet -> changeSet.get("name").equals(changeSetName))
        .findFirst()
        .map(changeSet -> (String) changeSet.get(field))
        .orElse(CloudFormationStates.NOT_YET_READY.toString());
  }

  private boolean isEmptyChangeSet(StageExecution stage, Map<String, ?> stack) {
    if ((boolean) Optional.ofNullable(stage.getContext().get("isChangeSet")).orElse(false)) {
      String status = getChangeSetInfo(stack, stage.getContext(), "status");
      String statusReason = getChangeSetInfo(stack, stage.getContext(), "statusReason");
      return status.equals(CloudFormationStates.FAILED.toString())
          && (statusReason.startsWith("The submitted information didn't contain changes")
              || statusReason.equals("No updates are to be performed."));
    } else {
      return false;
    }
  }

  private boolean isComplete(Object status) {
    if (status instanceof String) {
      return ((String) status).endsWith(CloudFormationStates.CREATE_COMPLETE.toString())
          || ((String) status).endsWith(CloudFormationStates.UPDATE_COMPLETE.toString())
          || ((String) status).endsWith(CloudFormationStates.DELETE_COMPLETE.toString());
    } else {
      return false;
    }
  }

  private boolean isInProgress(Object status) {
    if (status instanceof String) {
      return ((String) status).endsWith(CloudFormationStates.IN_PROGRESS.toString())
          || ((String) status).endsWith(CloudFormationStates.NOT_YET_READY.toString())
          || ((String) status).endsWith(CloudFormationStates.DELETE_IN_PROGRESS.toString());
    } else {
      return false;
    }
  }

  private boolean isFailed(Object status) {
    if (status instanceof String) {
      return ((String) status).endsWith(CloudFormationStates.ROLLBACK_COMPLETE.toString())
          || ((String) status).endsWith(CloudFormationStates.FAILED.toString())
          || ((String) status).endsWith(CloudFormationStates.DELETE_FAILED.toString());
    } else {
      return false;
    }
  }

  private enum CloudFormationStates {
    NOT_YET_READY,
    CREATE_COMPLETE,
    UPDATE_COMPLETE,
    IN_PROGRESS,
    ROLLBACK_COMPLETE,
    DELETE_FAILED,
    ROLLBACK_FAILED,
    UPDATE_ROLLBACK_FAILED,
    FAILED,
    DELETE_IN_PROGRESS,
    DELETE_COMPLETE;
  }
}
