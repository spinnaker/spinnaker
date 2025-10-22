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

package com.netflix.spinnaker.orca.api.pipeline.models;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * Execution Status enumerations used for executions and stages.
 *
 * <p>Note: we implement CharSequence to allow simple SpEL expressions such as: {#stage('s1').status
 * == 'SUCCEEDED'}
 */
public enum ExecutionStatus implements CharSequence {
  /** The task has yet to start. */
  NOT_STARTED(false, false),

  /** The task is still running and the {@code Task} may be re-executed in order to continue. */
  RUNNING(false, false),

  /** The task is still running and the {@code Task} may be resumed in order to continue. */
  PAUSED(false, false),

  /** The task is complete but the pipeline should now be stopped pending a trigger of some kind. */
  SUSPENDED(false, false),

  /** The task executed successfully and the pipeline may now proceed to the next task. */
  SUCCEEDED(true, false),

  /** The task execution failed, but the pipeline may proceed to the next task. */
  FAILED_CONTINUE(true, false),

  /** The task failed and the failure was terminal. The pipeline will not progress any further. */
  TERMINAL(true, true),

  /** The task was canceled. The pipeline will not progress any further. */
  CANCELED(true, true),

  /**
   * The step completed but is indicating that a decision path should be followed, not the default
   * path.
   */
  REDIRECT(false, false),

  /** The task was stopped. The pipeline will not progress any further. */
  STOPPED(true, true),

  /** The task was skipped and the pipeline will proceed to the next task. */
  SKIPPED(true, false),

  /** The task is not started and must be transitioned to NOT_STARTED. */
  BUFFERED(false, false);

  /** Indicates that the task/stage/pipeline has finished its work (successfully or not). */
  public final boolean isComplete() {
    return complete;
  }

  public final boolean isSkipped() {
    return SKIPPED.equals(this);
  }

  /** Indicates an abnormal completion so nothing downstream should run afterward. */
  public final boolean isHalt() {
    return halt;
  }

  public static final ImmutableSet<ExecutionStatus> COMPLETED =
      Sets.immutableEnumSet(CANCELED, SUCCEEDED, STOPPED, SKIPPED, TERMINAL, FAILED_CONTINUE);

  private static final ImmutableSet<ExecutionStatus> SUCCESSFUL =
      Sets.immutableEnumSet(SUCCEEDED, STOPPED, SKIPPED);

  private static final ImmutableSet<ExecutionStatus> FAILURE =
      Sets.immutableEnumSet(TERMINAL, STOPPED, FAILED_CONTINUE);

  private final boolean complete;
  private final boolean halt;

  ExecutionStatus(boolean complete, boolean halt) {
    this.complete = complete;
    this.halt = halt;
  }

  public boolean isSuccessful() {
    return SUCCESSFUL.contains(this);
  }

  public boolean isFailure() {
    return FAILURE.contains(this);
  }

  @Override
  public int length() {
    return toString().length();
  }

  @Override
  public char charAt(int index) {
    return toString().charAt(index);
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    return toString().subSequence(start, end);
  }
}
