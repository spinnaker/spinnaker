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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.kork.annotations.VisibleForTesting;

public class DefaultTaskStatus implements Status {

  public static DefaultTaskStatus create(String phase, String status, TaskState state) {
    return new DefaultTaskStatus(phase, status, state);
  }

  private final String phase;
  private final String status;
  @JsonIgnore private final TaskState state;

  public DefaultTaskStatus(String phase, String status, TaskState state) {
    this.phase = phase;
    this.status = status;
    this.state = state;
  }

  /**
   * This constructor is just for backwards-compatibility of tests from when the class was in Groovy
   * and could use hash-map constructors. This constructor must not be used in application code.
   */
  @VisibleForTesting
  public DefaultTaskStatus(TaskState state) {
    this.phase = null;
    this.status = null;
    this.state = state;
  }

  @JsonProperty
  public Boolean isComplete() {
    return state.isCompleted();
  }

  @JsonProperty
  public Boolean isCompleted() {
    return state.isCompleted();
  }

  @JsonProperty
  public Boolean isFailed() {
    return state.isFailed();
  }

  @JsonProperty
  public Boolean isRetryable() {
    return state.isRetryable();
  }

  public DefaultTaskStatus update(String phase, String status) {
    ensureUpdateable();
    return new DefaultTaskStatus(phase, status, state);
  }

  public DefaultTaskStatus update(TaskState state) {
    ensureUpdateable();
    return new DefaultTaskStatus(phase, status, state);
  }

  public void ensureUpdateable() {
    if (isCompleted()) {
      throw new IllegalStateException("Task is already completed! No further updates allowed!");
    }
  }

  public final String getPhase() {
    return phase;
  }

  public final String getStatus() {
    return status;
  }

  public final TaskState getState() {
    return state;
  }
}
