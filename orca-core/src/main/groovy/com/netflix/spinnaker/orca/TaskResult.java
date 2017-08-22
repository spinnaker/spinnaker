package com.netflix.spinnaker.orca;

import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.google.common.collect.ImmutableMap;
import static java.util.Collections.emptyMap;

public final class TaskResult {
  /**
   * A useful constant for a success result with no outputs.
   */
  public static final TaskResult SUCCEEDED = new TaskResult(ExecutionStatus.SUCCEEDED);

  private final ExecutionStatus status;
  private final Map<String, ?> stageOutputs;
  private final Map<String, ?> globalOutputs;

  public TaskResult(ExecutionStatus status) {
    this(status, emptyMap(), emptyMap());
  }

  public TaskResult(ExecutionStatus status, Map<String, ?> stageOutputs, Map<String, ?> globalOutputs) {
    this.status = status;
    this.stageOutputs = ImmutableMap.copyOf(stageOutputs);
    this.globalOutputs = ImmutableMap.copyOf(globalOutputs);
  }

  public TaskResult(ExecutionStatus status, Map<String, ?> stageOutputs) {
    this(status, stageOutputs, emptyMap());
  }

  @Deprecated
  public @Nonnull Map<String, ?> getOutputs() {
    return stageOutputs;
  }

  public @Nonnull ExecutionStatus getStatus() {
    return status;
  }

  public @Nonnull Map<String, ?> getStageOutputs() {
    return stageOutputs;
  }

  public @Nonnull Map<String, ?> getGlobalOutputs() {
    return globalOutputs;
  }
}
