/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.data.task

/**
 * This interface is used to represent the status of a Task for a point in time. Often should be backed by a POGO, but
 * may be used for more complex requirements, like querying a database or centralized task system in a multi-threaded/
 * multi-service environment.
 *
 * A pseudo-composite key of a Status is its phase and status strings.
 *
 *
 */
public interface Status {
  /**
   * Returns the current phase of the execution. This is useful for representing different parts of a Task execution, and
   * a "status" String will be tied
   */
  String getPhase()

  /**
   * Returns the current status of the Task in its given phase.
   */
  String getStatus()

  /**
   * Informs completion of the task.
   */
  Boolean isCompleted()

  /**
   * Informs whether the task has failed or not. A "failed" state is always indicative of a "completed" state.
   */
  Boolean isFailed()

  /**
   * Informs whether a failed task is retryable or not.
   */
  Boolean isRetryable()
}
