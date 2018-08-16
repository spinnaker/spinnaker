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
 * This interface represents the state of a given execution. Implementations must allow for updating and completing/failing
 * status, as well as providing the start time of the task.
 *
 *
 */
public interface Task {
  /**
   * A unique identifier for the task, which can be used to retrieve it at a later time.
   */
  String getId()

  /**
   * A list of result objects that are serialized back to the caller
   */
  List<Object> getResultObjects()

  /**
   * This method is used to add results objects to the Task
   * @param results
   */
  void addResultObjects(List<Object>results)

  /**
   * A comprehensive history of this task's execution.
   */
  List<? extends Status> getHistory()

  /**
   * The id of the clouddriver instance that submitted this task
    */
  String getOwnerId()

  /**
   * This method is used to update the status of the Task with given phase and status strings.
   * @param phase
   * @param status
   */
  void updateStatus(String phase, String status)

  /**
   * This method will complete the task and will represent completed = true from the Task's {@link #getStatus()} method.
   */
  void complete()

  /**
   * This method will fail the task and will represent completed = true and failed = true from the Task's
   * {@link #getStatus()} method.
   */
  void fail()

  /**
   * This method will return the current status of the task.
   * @see Status
   */
  Status getStatus()

  /**
   * This returns the start time of the Task's execution in milliseconds since epoch form.
   */
  long getStartTimeMs()
}
