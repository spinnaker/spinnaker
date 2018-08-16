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
 * A TaskRepository is an implementation that allows Task objects to be created, retrieved, and listed on demand.
 *
 * @see Task
 * @see InMemoryTaskRepository
 *
 */
public interface TaskRepository {
  /**
   * A thread local holder for a Task in-action. Useful for the {@link InMemoryTaskRepository} implementation.
   */
  static final ThreadLocal<Task> threadLocalTask = new ThreadLocal<>()

  /**
   * Creates a new task, and sets the initial status to the provided phase and status.
   *
   * @param phase
   * @param status
   * @return task
   */
  Task create(String phase, String status)

  /**
   * Creates a new task if a task has not already been created with that key
   * and sets the initial status to the provided phase and status.
   *
   * @param phase
   * @param status
   * @param clientRequestId
   * @return task the new task, or the previous task that was created with the supplied key
   */
  Task create(String phase, String status, String clientRequestId)

  /**
   * Retrieves a task by the provided id
   *
   * @param id
   * @return task
   */
  Task get(String id)

  /**
   * Retrieves a task by the provided clientRequestId
   * @param clientRequestId
   * @return task, or null if no task has been started with the requestId
   */
  Task getByClientRequestId(String clientRequestId)

  /**
   * Lists all tasks currently in the repository
   *
   * @return list of tasks
   */
  List<Task> list()

  /**
   * Lists all tasks owned by this instance
   */
  List<Task> listByThisInstance()
}
