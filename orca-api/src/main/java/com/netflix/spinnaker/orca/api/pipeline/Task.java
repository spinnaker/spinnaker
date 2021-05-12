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
 */
package com.netflix.spinnaker.orca.api.pipeline;

import com.netflix.spinnaker.kork.annotations.Beta;
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** A discrete unit of work in a pipeline execution that does one thing and one thing only. */
@Beta
public interface Task extends SpinnakerExtensionPoint {
  /**
   * Execute the business logic of the task, using the provided stage execution state.
   *
   * @param stage The running stage execution stage
   * @return The result of this Task's execution
   */
  @Nonnull
  TaskResult execute(@Nonnull StageExecution stage);

  /**
   * Behavior to be called on Task timeout.
   *
   * <p>This method should be used if you need to perform any cleanup operations in response to the
   * task being aborted after taking too long to complete.
   *
   * @param stage The running state execution state
   */
  default @Nullable TaskResult onTimeout(@Nonnull StageExecution stage) {
    return null;
  }

  /**
   * Behavior to be called on Task cancellation.
   *
   * <p>This method should be used if you need to perform cleanup in response to the task being
   * cancelled before it was able to complete.
   *
   * @deprecated Use onCancelWithResult instead
   * @param stage The running state execution state
   */
  @Deprecated
  default void onCancel(@Nonnull StageExecution stage) {}

  /**
   * Behavior to be called on Task cancellation.
   *
   * <p>This method should be used if you need to perform cleanup in response to the task being
   * cancelled before it was able to complete.
   *
   * <p>When returning a {@link TaskResult}, the {@link ExecutionStatus} will be ignored, as the
   * resulting status will always be {@link ExecutionStatus#CANCELED}.
   *
   * @param stage The running state execution state
   */
  @Nullable
  default TaskResult onCancelWithResult(@Nonnull StageExecution stage) {
    onCancel(stage);
    return null;
  }

  /** A collection of known aliases. */
  default Collection<String> aliases() {
    if (getClass().isAnnotationPresent(Aliases.class)) {
      return Arrays.asList(getClass().getAnnotation(Aliases.class).value());
    }

    return Collections.emptyList();
  }

  /** Allows backwards compatibility of a task's "type", even through class renames / refactors. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  @interface Aliases {
    String[] value() default {};
  }
}
