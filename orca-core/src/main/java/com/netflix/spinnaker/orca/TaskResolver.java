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
import com.netflix.spinnaker.orca.api.simplestage.SimpleStage;
import com.netflix.spinnaker.orca.pipeline.SimpleTask;
import java.util.*;
import javax.annotation.Nonnull;

/**
 * {@code TaskResolver} allows for {@code Task} retrieval via class name or alias.
 *
 * <p>Aliases represent the previous class names of a {@code Task}.
 */
public class TaskResolver {
  private final Map<String, Task> taskByAlias = new HashMap<>();

  private final boolean allowFallback;

  @VisibleForTesting
  public TaskResolver(Collection<Task> tasks) {
    this(tasks, true);
  }

  /**
   * @param tasks Task implementations
   * @param allowFallback Fallback to {@code Class.forName()} if a task cannot be located by name or
   *     alias
   */
  public TaskResolver(Collection<Task> tasks, boolean allowFallback) {
    this(tasks, Collections.emptyList(), true);
  }

  public TaskResolver(Collection<Task> tasks, List<SimpleStage> stages, boolean allowFallback) {
    for (Task task : tasks) {
      taskByAlias.put(task.getClass().getCanonicalName(), task);
      for (String alias : task.aliases()) {
        if (taskByAlias.containsKey(alias)) {
          throw new DuplicateTaskAliasException(
              String.format(
                  "Duplicate task alias detected (alias: %s, previous: %s, current: %s)",
                  alias,
                  taskByAlias.get(alias).getClass().getCanonicalName(),
                  task.getClass().getCanonicalName()));
        }

        taskByAlias.put(alias, task);
      }
    }
    for (SimpleStage stage : stages) {
      taskByAlias.put(stage.getClass().getCanonicalName(), new SimpleTask(stage));
    }

    this.allowFallback = allowFallback;
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
      throw new NoSuchTaskException(taskTypeIdentifier);
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
    Task matchingTask =
        taskByAlias.values().stream()
            .filter((Task task) -> taskType.isAssignableFrom(task.getClass()))
            .findFirst()
            .orElseThrow(() -> new NoSuchTaskException(taskType.getCanonicalName()));
    return matchingTask;
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
      return (Class<? extends Task>) task.getClass();
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
