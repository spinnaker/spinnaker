/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca;

import java.util.Map;
import javax.annotation.Nonnull;
import com.google.common.collect.ImmutableMap;
import static java.util.Collections.emptyMap;

public final class TaskResult {
  /**
   * A useful constant for a success result with no outputs.
   */
  public static final TaskResult SUCCEEDED = new TaskResult(ExecutionStatus.SUCCEEDED);
  public static final TaskResult RUNNING = new TaskResult(ExecutionStatus.RUNNING);

  private final ExecutionStatus status;
  private final ImmutableMap<String, ?> context;
  private final ImmutableMap<String, ?> outputs;

  public TaskResult(ExecutionStatus status) {
    this(status, emptyMap(), emptyMap());
  }

  public TaskResult(ExecutionStatus status, Map<String, ?> context, Map<String, ?> outputs) {
    this.status = status;
    this.context = ImmutableMap.copyOf(context);
    this.outputs = ImmutableMap.copyOf(outputs);
  }

  public TaskResult(ExecutionStatus status, Map<String, ?> context) {
    this(status, context, emptyMap());
  }

  public @Nonnull ExecutionStatus getStatus() {
    return status;
  }

  /**
   * Updates to the current stage context.
   */
  public @Nonnull Map<String, ?> getContext() {
    return context;
  }

  /**
   * Values to be output from the stage and potentially accessed by downstream
   * stages.
   */
  public @Nonnull Map<String, ?> getOutputs() {
    return outputs;
  }

  @Override
  public String toString() {
    return "TaskResult{" +
            "status=" + status +
            ", context=" + context +
            ", outputs=" + outputs +
            '}';
  }
}
