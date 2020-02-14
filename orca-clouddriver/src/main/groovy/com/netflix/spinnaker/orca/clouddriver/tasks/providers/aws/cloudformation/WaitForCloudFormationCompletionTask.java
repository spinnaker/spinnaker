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

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
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
import retrofit.RetrofitError;

@Slf4j
@Component
public class WaitForCloudFormationCompletionTask implements OverridableTimeoutRetryableTask {

  public static final String TASK_NAME = "waitForCloudFormationCompletion";

  private enum CloudFormationStates {
    NOT_YET_READY,
    CREATE_COMPLETE,
    UPDATE_COMPLETE,
    IN_PROGRESS,
    ROLLBACK_COMPLETE,
    FAILED
  }

  private final long backoffPeriod = TimeUnit.SECONDS.toMillis(10);
  private final long timeout = TimeUnit.HOURS.toMillis(2);

  @Autowired private OortService oortService;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    try {
      Map task = ((List<Map>) stage.getContext().get("kato.tasks")).iterator().next();
      Map result = ((List<Map>) task.get("resultObjects")).iterator().next();
      String stackId = (String) result.get("stackId");
      Map<String, ?> stack = (Map<String, Object>) oortService.getCloudFormationStack(stackId);
      log.info(
          "Received cloud formation stackId "
              + stackId
              + " with status "
              + stack.get("stackStatus"));

      boolean isChangeSet =
          (boolean) Optional.ofNullable(stage.getContext().get("isChangeSet")).orElse(false);
      boolean isChangeSetExecution =
          (boolean)
              Optional.ofNullable(stage.getContext().get("isChangeSetExecution")).orElse(false);
      boolean isChangeSetDeletion =
          (boolean) Optional.ofNullable(stage.getContext().get("deleteChangeSet")).orElse(false);

      log.info("Deploying a CloudFormation ChangeSet for stackId " + stackId + ": " + isChangeSet);

      String status =
          (isChangeSet && !isChangeSetExecution && !isChangeSetDeletion)
              ? getChangeSetInfo(stack, stage.getContext(), "status")
              : getStackInfo(stack, "stackStatus");

      if (isComplete(status)) {
        return TaskResult.builder(ExecutionStatus.SUCCEEDED).outputs(stack).build();
      } else if (isEmptyChangeSet(stage, stack)) {
        String changeSetName = (String) result.get("changeSetName");
        log.info("CloudFormation ChangeSet {} empty. Requesting to be deleted.", changeSetName);
        return TaskResult.builder(ExecutionStatus.SUCCEEDED)
            .context("deleteChangeSet", true)
            .outputs(stack)
            .build();
      } else if (isInProgress(status)) {
        return TaskResult.RUNNING;
      } else if (isFailed(status)) {
        String statusReason =
            isChangeSet
                ? getChangeSetInfo(stack, stage.getContext(), "statusReason")
                : getStackInfo(stack, "stackStatusReason");
        log.info("Cloud formation stack failed to completed. Status: " + statusReason);
        throw new RuntimeException(statusReason);
      }
      throw new RuntimeException("Unexpected stack status: " + stack.get("stackStatus"));
    } catch (RetrofitError e) {
      if (e.getResponse().getStatus() == HttpStatus.NOT_FOUND.value()) {
        // The cache might not be up to date, try in the next iteration.
        return TaskResult.RUNNING;
      } else {
        log.error("Error retrieving cloud formation stack", e);
        throw new RuntimeException(e.getMessage());
      }
    }
  }

  @Override
  public long getBackoffPeriod() {
    return backoffPeriod;
  }

  @Override
  public long getTimeout() {
    return timeout;
  }

  private String getStackInfo(Map stack, String field) {
    return (String) stack.get(field);
  }

  private String getChangeSetInfo(Map stack, Map context, String field) {
    String changeSetName = (String) context.get("changeSetName");
    log.debug("Getting change set status from stack for changeset {}: {}", changeSetName, stack);
    return Optional.ofNullable((List<Map<String, ?>>) stack.get("changeSets"))
        .orElse(Collections.emptyList()).stream()
        .filter(changeSet -> changeSet.get("name").equals(changeSetName))
        .findFirst()
        .map(changeSet -> (String) changeSet.get(field))
        .orElse(CloudFormationStates.NOT_YET_READY.toString());
  }

  private boolean isEmptyChangeSet(Stage stage, Map<String, ?> stack) {
    if ((boolean) Optional.ofNullable(stage.getContext().get("isChangeSet")).orElse(false)) {
      String status = getChangeSetInfo(stack, stage.getContext(), "status");
      String statusReason = getChangeSetInfo(stack, stage.getContext(), "statusReason");
      return status.equals(CloudFormationStates.FAILED.toString())
          && statusReason.startsWith("The submitted information didn't contain changes");
    } else {
      return false;
    }
  }

  private boolean isComplete(Object status) {
    if (status instanceof String) {
      return ((String) status).endsWith(CloudFormationStates.CREATE_COMPLETE.toString())
          || ((String) status).endsWith(CloudFormationStates.UPDATE_COMPLETE.toString());
    } else {
      return false;
    }
  }

  private boolean isInProgress(Object status) {
    if (status instanceof String) {
      return ((String) status).endsWith(CloudFormationStates.IN_PROGRESS.toString())
          || ((String) status).endsWith(CloudFormationStates.NOT_YET_READY.toString());
    } else {
      return false;
    }
  }

  private boolean isFailed(Object status) {
    if (status instanceof String) {
      return ((String) status).endsWith(CloudFormationStates.ROLLBACK_COMPLETE.toString())
          || ((String) status).endsWith(CloudFormationStates.FAILED.toString());
    } else {
      return false;
    }
  }
}
