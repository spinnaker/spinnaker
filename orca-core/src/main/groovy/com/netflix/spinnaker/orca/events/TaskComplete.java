/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.events;

import javax.annotation.Nonnull;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.Task;

public class TaskComplete extends ExecutionEvent {
  private final String stageId;
  private final String stageType;
  private final String stageName;
  private final String taskId;
  private final String taskType;
  private final String taskName;
  private final ExecutionStatus status;

  public TaskComplete(
    @Nonnull Object source,
    @Nonnull Class<? extends Execution> executionType,
    @Nonnull String executionId,
    @Nonnull String stageId,
    @Nonnull String stageType,
    @Nonnull String stageName,
    @Nonnull String taskId,
    @Nonnull String taskType,
    @Nonnull String taskName,
    @Nonnull ExecutionStatus status
  ) {
    super(source, executionType, executionId);
    this.stageId = stageId;
    this.stageType = stageType;
    this.stageName = stageName;
    this.taskId = taskId;
    this.taskType = taskType;
    this.taskName = taskName;
    this.status = status;
  }

  public TaskComplete(
    @Nonnull Object source,
    @Nonnull Stage<? extends Execution<?>> stage,
    @Nonnull Task task
  ) {
    this(
      source,
      stage.getExecution().getClass(),
      stage.getExecution().getId(),
      stage.getId(),
      stage.getType(),
      stage.getName(),
      task.getId(),
      task.getImplementingClass(),
      task.getName(),
      task.getStatus()
    );
  }

  public @Nonnull String getStageId() {
    return stageId;
  }

  public @Nonnull String getStageType() {
    return stageType;
  }

  public @Nonnull String getStageName() {
    return stageName;
  }

  public @Nonnull String getTaskId() {
    return taskId;
  }

  public @Nonnull String getTaskType() {
    return taskType;
  }

  public @Nonnull String getTaskName() {
    return taskName;
  }

  public @Nonnull ExecutionStatus getStatus() {
    return status;
  }
}
