/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.spinnaker.orca.api.pipeline;

import com.netflix.spinnaker.kork.annotations.Beta;
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.util.concurrent.TimeUnit;

/**
 * TaskExecutionInterceptor is a hook point to customize the specific execution of a task.
 *
 * <p>Before execute is called on a Task, all TaskExecutionInterceptors will be called. The
 * resulting Stage object from beforeTaskExecution is passed to subsequent invocations of
 * TaskExecutionInterceptor and then used for the invocation of execute on Task.
 *
 * <p>After a Task completes with a TaskResult, all TaskExecutionInterceptors are called. The
 * resulting TaskResult is passed to subsequent invocations ot TaskExecutionInterceptor and the
 * final TaskResult is used as the output of the task.
 *
 * <p>A TaskExecutionInterceptor can specify the maximum backoff that should be allowed. As an
 * example, the LockExtendingTaskExecutionInterceptor needs to ensure that a task doesn't delay
 * longer than the lock extension. The minimum maxTaskBackoff among all registered
 * TaskExecutionInterceptors will be used to constrain the task backoff supplied by a RetryableTask.
 */
@Beta
public interface TaskExecutionInterceptor extends SpinnakerExtensionPoint {

  /**
   * hook that is called to program the collective maximum backoff of all TaskExecutionInterceptors.
   * A retryable task has a backoff period which is configured by defaults and then constrained by
   * the minimum backoff configured by all registered interceptors.
   *
   * @return long
   */
  default long maxTaskBackoff() {
    return TimeUnit.MINUTES.toMillis(2);
  }

  /**
   * hook that is called before a task executes.
   *
   * @param task The task that is being handled.
   * @param stage The stage for the task execution.
   * @return stage The stage for the task execution.
   */
  default StageExecution beforeTaskExecution(Task task, StageExecution stage) {
    return stage;
  }

  /**
   * hook that is called after a task executes successfully.
   *
   * <p>As an example you can modify the taskResult here before it gets propagated.
   *
   * @param task The task that is being handled.
   * @param stage The stage for the task execution.
   * @param taskResult The result of executing the task.
   * @return taskResult The result of executing the task.
   */
  default TaskResult afterTaskExecution(Task task, StageExecution stage, TaskResult taskResult) {
    return taskResult;
  }

  /**
   * hook that is guaranteed to be called, even when a task throws an exception which will cause
   * afterTaskExecution to not be called.
   *
   * <p>As an example you can clear the security context here if you set it in the
   * beforeTaskExecution hook.
   *
   * @param task The task that is being handled.
   * @param stage The stage for the task execution.
   * @param taskResult Will be null if the task failed with an exception.
   * @param e Will be not null if the task failed with an exception.
   */
  default void finallyAfterTaskExecution(
      Task task, StageExecution stage, TaskResult taskResult, Exception e) {}
}
