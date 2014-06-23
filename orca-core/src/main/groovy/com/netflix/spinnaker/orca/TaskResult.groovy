/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca

import com.google.common.collect.ImmutableMap

interface TaskResult {

  Status getStatus()

  ImmutableMap<String, Object> getOutputs()

  /**
   * Indicates the state of the workflow at the end of a call to {@link Task#execute}.
   */
  static enum Status {
    /**
     * The task is still running and the {@code Task} may be re-executed in order to continue.
     */
    RUNNING(false),

    /**
     * The task is complete but the workflow should now be stopped pending a trigger of some kind.
     */
      SUSPENDED(false),

    /**
     * The task executed successfully and the workflow may now proceed to the next task.
     */
      SUCCEEDED(true),

    /**
     * The task failed and the workflow should stop with an error.
     */
      FAILED(true)

    final boolean complete

    Status(boolean complete) {
      this.complete = complete
    }
  }
}

