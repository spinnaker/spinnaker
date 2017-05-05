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

import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.Task;

public class TaskStarted extends ExecutionEvent {
  private final String stageId;
  private final String stageType;
  private final String stageName;
  private final String taskId;
  private final String taskType;
  private final String taskName;

  public TaskStarted(Object source, Class<? extends Execution> executionType, String executionId, String stageId, String stageType, String stageName, String taskId, String taskType, String taskName) {
    super(source, executionType, executionId);
    this.stageId = stageId;
    this.stageType = stageType;
    this.stageName = stageName;
    this.taskId = taskId;
    this.taskType = taskType;
    this.taskName = taskName;
  }

  public TaskStarted(Object source, Stage<? extends Execution<?>> stage, Task task) {
    this(
      source,
      stage.getExecution().getClass(),
      stage.getExecution().getId(),
      stage.getId(),
      stage.getType(),
      stage.getName(),
      task.getId(),
      task.getImplementingClass(),
      task.getName()
    );
  }

  public String getStageId() {
    return stageId;
  }

  public String getStageType() {
    return stageType;
  }

  public String getStageName() {
    return stageName;
  }

  public String getTaskId() {
    return taskId;
  }

  public String getTaskType() {
    return taskType;
  }

  public String getTaskName() {
    return taskName;
  }
}
