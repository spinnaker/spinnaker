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
    var tasks = tasksProvider.getIfAvailable(ArrayList::new);
    for (Task task : tasks) {
      taskByAlias.put(task.getExtensionClass().getCanonicalName(), task);
      addAliases(task);
    }
  }

  /**
   * Fetch a {@code Task} by {@code taskTypeIdentifier}.
   *
   * @param taskTypeIdentifier Task identifier (class name or alias)
   * @return the Task matching {@code taskTypeIdentifier}
   * @throws NoSuchTaskException if Task does not exist
   */
  @Nonnull
  public Task getTask(@Nonnull String taskTypeIdentifier) {
    Task task = taskByAlias.get(taskTypeIdentifier);

    if (task == null) {
      log.debug(
          "{} task not found in task cache, fetching missing tasks from task provider.",
          taskTypeIdentifier);
      addMissingTasksFromTaskProvider();
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
   * @param taskType Task type (class of task)
   * @return the Task matching {@code taskType}
   * @throws NoSuchTaskException if Task does not exist
   */
  @Nonnull
  public Task getTask(@Nonnull Class<? extends Task> taskType) {
    Optional<Task> optionalTask =
        taskByAlias.values().stream()
            .filter((Task task) -> taskType.isAssignableFrom(task.getExtensionClass()))
            .findFirst();

    if (!optionalTask.isPresent()) {
      log.debug(
          "{} task not found in task cache, fetching missing tasks from task provider.",
          taskType.toString());
      addMissingTasksFromTaskProvider();
      return taskByAlias.values().stream()
          .filter((Task task) -> taskType.isAssignableFrom(task.getExtensionClass()))
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

  /** Lookup tasks from the task provider and add missing tasks to taskByAlias cache. */
  private void addMissingTasksFromTaskProvider() {
    for (Task task : tasksProvider.getIfAvailable(ArrayList::new)) {
      if (taskByAlias.get(task.getExtensionClass().getCanonicalName()) == null) {
        taskByAlias.put(task.getExtensionClass().getCanonicalName(), task);
        addAliases(task);
        log.info(
            "{} task resolved from task provider and added to cache",
            task.getExtensionClass().toString());
      }
    }
  }

  /**
   * Add task aliases to taskByAlias map, throwing {@link DuplicateTaskAliasException} if duplicates
   * are detected.
   */
  private void addAliases(Task task) {
    for (String alias : task.aliases()) {
      if (taskByAlias.containsKey(alias)) {
        throw new DuplicateTaskAliasException(
            String.format(
                "Duplicate task alias detected (alias: %s, previous: %s, current: %s)",
                alias,
                taskByAlias.get(alias).getExtensionClass().getCanonicalName(),
                task.getExtensionClass().getCanonicalName()));
      }

      taskByAlias.put(alias, task);
    }
  }
}
