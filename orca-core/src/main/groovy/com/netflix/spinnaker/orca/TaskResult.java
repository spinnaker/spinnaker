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
}
