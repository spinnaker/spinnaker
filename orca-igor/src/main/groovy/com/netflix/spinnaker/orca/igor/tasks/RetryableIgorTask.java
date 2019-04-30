/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.orca.igor.tasks;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.igor.model.RetryableStageDefinition;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import retrofit.RetrofitError;

@RequiredArgsConstructor
@Slf4j
public abstract class RetryableIgorTask<T extends RetryableStageDefinition> implements RetryableTask {
  public long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(5);
  }

  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(1);
  }

  protected int getMaxConsecutiveErrors() {
    return 5;
  }

  @Override
  public @Nonnull TaskResult execute(@Nonnull Stage stage) {
    T stageDefinition = mapStage(stage);
    int errors = stageDefinition.getConsecutiveErrors();
    try {
      TaskResult result = tryExecute(stageDefinition);
      return resetErrorCount(result);
    } catch (RetrofitError e) {
      if (stageDefinition.getConsecutiveErrors() < getMaxConsecutiveErrors() && isRetryable(e)) {
        return TaskResult.builder(ExecutionStatus.RUNNING).context(errorContext(errors + 1)).build();
      }
      throw e;
    }
  }

  abstract protected @Nonnull TaskResult tryExecute(@Nonnull T stageDefinition);

  abstract protected @Nonnull T mapStage(@Nonnull Stage stage);

  private TaskResult resetErrorCount(TaskResult result) {
    Map<String, Object> newContext = ImmutableMap.<String, Object>builder()
      .putAll(result.getContext())
      .put("consecutiveErrors", 0)
      .build();
    return TaskResult.builder(result.getStatus()).context(newContext).outputs(result.getOutputs()).build();
  }

  private Map<String, Integer> errorContext(int errors) {
    return Collections.singletonMap("consecutiveErrors", errors);
  }

  private boolean isRetryable(RetrofitError retrofitError) {
    if (retrofitError.getKind() == RetrofitError.Kind.NETWORK) {
      log.warn("Failed to communicate with igor, retrying...");
      return true;
    }

    int status = retrofitError.getResponse().getStatus();
    if (status == 500 || status == 503) {
      log.warn(String.format("Received HTTP %s response from igor, retrying...", status));
      return true;
    }
    return false;
  }
}
