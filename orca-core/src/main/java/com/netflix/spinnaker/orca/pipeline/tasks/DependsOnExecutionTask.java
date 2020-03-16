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

package com.netflix.spinnaker.orca.pipeline.tasks;

import static java.lang.String.format;

import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DependsOnExecutionTask implements OverridableTimeoutRetryableTask {

  private final ExecutionRepository repository;

  @Autowired
  public DependsOnExecutionTask(ExecutionRepository repository) {
    this.repository = repository;
  }

  @Override
  public long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(10);
  }

  @Override
  public long getTimeout() {
    return TimeUnit.HOURS.toMillis(2);
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    TaskContext context = stage.mapTo(TaskContext.class);

    try {
      PipelineExecution execution =
          repository.retrieve(
              ExecutionType.valueOf(context.executionType.toUpperCase()), context.executionId);
      ExecutionStatus status = execution.getStatus();

      if (status.isSuccessful()) {
        return TaskResult.SUCCEEDED;
      }

      if (status.isComplete()) {
        return TaskResult.builder(ExecutionStatus.CANCELED)
            .context("reason", format("Depended-on execution completed with status %s", status))
            .build();
      }

      return TaskResult.RUNNING;
    } catch (ExecutionNotFoundException e) {
      return TaskResult.builder(ExecutionStatus.TERMINAL)
          .context(
              "error",
              format("Execution (%s) %s not found.", context.executionType, context.executionId))
          .build();
    }
  }

  private static class TaskContext {
    @NotNull public String executionType;
    @NotNull public String executionId;
  }
}
