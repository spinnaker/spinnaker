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
 *
 */
package com.netflix.spinnaker.clouddriver.data.task;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class TaskDisplayStatus implements Status {

  public static TaskDisplayStatus create(Status taskStatus) {
    return new TaskDisplayStatus(taskStatus);
  }

  @JsonIgnore private final Status taskStatus;

  public TaskDisplayStatus(Status taskStatus) {
    this.taskStatus = taskStatus;
  }

  @Override
  public String getStatus() {
    return taskStatus.getStatus();
  }

  @Override
  public String getPhase() {
    return taskStatus.getPhase();
  }

  @JsonIgnore
  public Boolean isCompleted() {
    return taskStatus.isCompleted();
  }

  @JsonIgnore
  public Boolean isFailed() {
    return taskStatus.isFailed();
  }

  @JsonIgnore
  @Override
  public Boolean isRetryable() {
    return taskStatus.isRetryable();
  }

  public final Status getTaskStatus() {
    return taskStatus;
  }
}
