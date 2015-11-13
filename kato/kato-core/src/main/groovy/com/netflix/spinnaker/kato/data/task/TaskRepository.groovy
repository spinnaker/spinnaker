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

package com.netflix.spinnaker.kato.data.task
/**
 * A TaskRepository is an implementation that allows Task objects to be created, retrieved, and listed on demand.
 *
 * @see com.netflix.spinnaker.kato.data.task.Task
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
   * Retrieves a task by the provided id
   *
   * @param id
   * @return task
   */
  Task get(String id)

  /**
   * Lists all tasks currently in the repository
   *
   * @return list of tasks
   */
  List<Task> list()
}
