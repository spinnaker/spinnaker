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

import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class WaitForCloudFormationCompletionTask implements OverridableTimeoutRetryableTask {

  public static final String TASK_NAME = "waitForCloudFormationCompletion";

  private final long backoffPeriod = TimeUnit.SECONDS.toMillis(10);
  private final long timeout = TimeUnit.HOURS.toMillis(2);

  @Autowired
  private OortService oortService;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    try {
      Map task = ((List<Map>) stage.getContext().get("kato.tasks")).iterator().next();
      Map result = ((List<Map>) task.get("resultObjects")).iterator().next();
      String stackId = (String) result.get("stackId");
      Map stack = oortService.getCloudFormationStack(stackId);
      log.info("Received cloud formation stackId " + stackId + " with status " + stack.get("stackStatus"));
      if (isComplete(stack.get("stackStatus"))) {
        return TaskResult.SUCCEEDED;
      } else if (isInProgress(stack.get("stackStatus"))) {
        return TaskResult.RUNNING;
      } else if (isFailed(stack.get("stackStatus"))) {
        log.info("Cloud formation stack failed to completed. Status: " + stack.get("stackStatusReason"));
        throw new RuntimeException((String) stack.get("stackStatusReason"));
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

  private boolean isComplete(Object status) {
    if (status instanceof String) {
      return ((String) status).endsWith("CREATE_COMPLETE") || ((String) status).endsWith("UPDATE_COMPLETE");
    } else {
      return false;
    }
  }

  private boolean isInProgress(Object status) {
    if (status instanceof String) {
      return ((String) status).endsWith("IN_PROGRESS");
    } else {
      return false;
    }
  }

  private boolean isFailed(Object status) {
    if (status instanceof String) {
      return ((String) status).endsWith("ROLLBACK_COMPLETE");
    } else {
      return false;
    }
  }
}
