/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca

import groovy.transform.CompileStatic

@CompileStatic
enum ExecutionStatus {

  /**
   * The task has yet to start.
   */
  NOT_STARTED(false, false),

  /**
   * The task is still running and the {@code Task} may be re-executed in order
   * to continue.
   */
    RUNNING(false, false),

  /**
   * The task is complete but the pipeline should now be stopped pending a
   * trigger of some kind.
   */
    SUSPENDED(false, false),

  /**
   * The task executed successfully and the pipeline may now proceed to the next
   * task.
   */
    SUCCEEDED(true, false),

  /**
   * The task failed and the pipeline should be able to recover through
   * subsequent steps.
   */
    FAILED(true, true),

  /**
   * The task failed and the failure was terminal. The pipeline will not
   * progress any further.
   */
    TERMINAL(true, true),

  /**
   * The task was canceled. The pipeline will not progress any further.
   */
    CANCELED(true, true),

  /**
   * The step completed but is indicating that a decision path should be followed, not the default path.
   */
    REDIRECT(true, false)

  /**
   * Indicates that the task/stage/pipeline has finished its work (successfully or not).
   */
  final boolean complete

  /**
   * Indicates an abnormal completion so nothing downstream should run afterward.
   */
  final boolean halt

  ExecutionStatus(boolean complete, boolean halt) {
    this.complete = complete
    this.halt = halt
  }
}
