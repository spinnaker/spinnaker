/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

/**
 * {@code TaskResolver} allows for {@code Task} retrieval via class name or alias.
 *
 * <p>Aliases represent the previous class names of a {@code Task}.
 */
public class TaskResolver {
  private final ConcurrentHashMap<String, Task> taskByAlias = new ConcurrentHashMap<>();
  private final ObjectProvider<Collection<Task>> tasksProvider;

  private final boolean allowFallback;

  private final Logger log = LoggerFactory.getLogger(getClass());

  @VisibleForTesting
  public TaskResolver(ObjectProvider<Collection<Task>> tasksProvider) {
    this(tasksProvider, true);
  }

  /**
   * @param tasksProvider Task implementations
   * @param allowFallback Fallback to {@code Class.forName()} if a task cannot be located by name or
   *     alias
   */
  public TaskResolver(ObjectProvider<Collection<Task>> tasksProvider, boolean allowFallback) {
    this.allowFallback = allowFallback;
    this.tasksProvider = tasksProvider;
    computeTasks();
  }

  /**
   * Compute a cache of tasks. A local map is first built and validated for duplicate tasks and then
   * copied to the thread safe cache.
   *
   * <p>This allows us to re-compute the tasks cache if necessary (on a cache miss for example) and
   * ensures the validation always runs correctly against the latest set of {@link Task} classes.
   */
  private void computeTasks() {
    Collection<Task> tasks = tasksProvider.getIfAvailable(ArrayList::new);
    Map<String, Task> localTasksByAlias = new HashMap<>();

    for (Task task : tasks) {
      localTasksByAlias.put(task.getExtensionClass().getCanonicalName(), task);
      for (String alias : task.aliases()) {
        if (localTasksByAlias.containsKey(alias)) {
          throw new DuplicateTaskAliasException(
              String.format(
                  "Duplicate task alias detected (alias: %s, previous: %s, current: %s)",
                  alias,
                  localTasksByAlias.get(alias).getClass().getCanonicalName(),
                  task.getExtensionClass().getCanonicalName()));
        }

        localTasksByAlias.put(alias, task);
      }
    }
    taskByAlias.putAll(localTasksByAlias);
  }

  /**
   * Fetch a {@code Task} by {@code taskTypeIdentifier}.
   *
   * <p>If the task is not found from the type, attempts to re-compute tasks and lookup again.
   *
   * @param taskTypeIdentifier Task identifier (class name or alias)
   * @return the Task matching {@code taskTypeIdentifier}
   * @throws NoSuchTaskException if Task does not exist
   */
  @Nonnull
  public Task getTask(@Nonnull String taskTypeIdentifier) {
    Task task = taskByAlias.get(taskTypeIdentifier);

    if (task == null) {
      computeTasks();
      log.debug(
          "Task type '{}' not found in initial task cache, re-computing...", taskTypeIdentifier);
      task = taskByAlias.get(taskTypeIdentifier);
      if (task == null) {
        throw new NoSuchTaskException(taskTypeIdentifier);
      }
    }

    return task;
  }

  /**
   * Fetch a {@code Task} by {@code Class type}.
   *
   * <p>If the task is not found from the type, attempts to re-compute tasks and lookup again.
   *
   * @param taskType Task type (class of task)
   * @return the Task matching {@code taskType}
   * @throws NoSuchTaskException if Task does not exist
   */
  @Nonnull
  public Task getTask(@Nonnull Class<? extends Task> taskType) {
    Optional<Task> optionalTask =
        taskByAlias.values().stream()
            .filter((Task task) -> taskType.isAssignableFrom(task.getClass()))
            .findFirst();

    if (!optionalTask.isPresent()) {
      log.debug(
          "Task type '{}' not found in initial task cache, re-computing...", taskType.toString());
      computeTasks();
      return taskByAlias.values().stream()
          .filter((Task task) -> taskType.isAssignableFrom(task.getClass()))
          .findFirst()
          .orElseThrow(() -> new NoSuchTaskException(taskType.getCanonicalName()));
    }

    return optionalTask.get();
  }

  /**
   * @param taskTypeIdentifier Task identifier (class name or alias)
   * @return Task Class
   * @throws NoSuchTaskException if task does not exist
   */
  @Nonnull
  public Class<? extends Task> getTaskClass(@Nonnull String taskTypeIdentifier) {
    try {
      Task task = getTask(taskTypeIdentifier);
      return (Class<? extends Task>) task.getExtensionClass();
    } catch (IllegalArgumentException e) {
      if (!allowFallback) {
        throw e;
      }

      try {
        return (Class<? extends Task>) Class.forName(taskTypeIdentifier);
      } catch (ClassNotFoundException ex) {
        throw e;
      }
    }
  }

  public class DuplicateTaskAliasException extends IllegalStateException {
    DuplicateTaskAliasException(String message) {
      super(message);
    }
  }

  public class NoSuchTaskException extends IllegalArgumentException {
    NoSuchTaskException(String taskTypeIdentifier) {
      super("No task found for '" + taskTypeIdentifier + "'");
    }
  }
}
