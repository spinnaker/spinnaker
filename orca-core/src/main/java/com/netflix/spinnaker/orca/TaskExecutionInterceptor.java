/*
 * Copyright 2018 Netflix, Inc.
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

import com.netflix.spinnaker.orca.pipeline.model.Stage;

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
public interface TaskExecutionInterceptor {

  default long maxTaskBackoff() {
    return Long.MAX_VALUE;
  }

  default Stage beforeTaskExecution(Task task, Stage stage) {
    return stage;
  }

  default TaskResult afterTaskExecution(Task task, Stage stage, TaskResult taskResult) {
    return taskResult;
  }
}
