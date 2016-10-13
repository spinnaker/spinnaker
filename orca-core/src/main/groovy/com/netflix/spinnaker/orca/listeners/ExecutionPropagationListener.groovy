/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.listeners

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Execution
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.orca.ExecutionStatus.*

@Slf4j
@CompileStatic
class ExecutionPropagationListener implements ExecutionListener {
  private boolean isBeforeJobEnabled = false
  private boolean isAfterJobEnabled = false

  ExecutionPropagationListener(boolean isBeforeJobEnabled, boolean isAfterJobEnabled) {
    this.isBeforeJobEnabled = isBeforeJobEnabled
    this.isAfterJobEnabled = isAfterJobEnabled
  }

  @Override
  void beforeExecution(Persister persister, Execution execution) {
    if (!isBeforeJobEnabled) {
      return
    }

    persister.updateStatus(execution.id, RUNNING)
    log.info("Marked ${execution.id} as $RUNNING (beforeJob)");
  }

  @Override
  public void afterExecution(Persister persister,
                             Execution execution,
                             ExecutionStatus executionStatus,
                             boolean wasSuccessful) {
    if (!isAfterJobEnabled) {
      return
    }

    if (persister.isCanceled(execution.id) && executionStatus != TERMINAL) {
      executionStatus = CANCELED
    }

    if (executionStatus == STOPPED) {
      executionStatus = SUCCEEDED
    }

    if (!executionStatus) {
      executionStatus = TERMINAL
    }

    persister.updateStatus(execution.id, executionStatus)
    log.info("Marked ${execution.id} as ${executionStatus} (afterJob)")
  }

  @Override
  int getOrder() {
    if (isBeforeJobEnabled) {
      return HIGHEST_PRECEDENCE
    }

    if (isAfterJobEnabled) {
      return LOWEST_PRECEDENCE
    }

    return 0
  }
}
