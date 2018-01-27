package com.netflix.spinnaker.orca.pipeline.tasks;

import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.pipeline.CheckPreconditionsStage;

/**
 * Interface for tasks used to evaluate a precondition.
 * <p>
 * A precondition task is intended to evaluates current state without any
 * side-effects. It will be executed by {@link CheckPreconditionsStage}.
 */
public interface PreconditionTask extends Task {
  String getPreconditionType();
}
