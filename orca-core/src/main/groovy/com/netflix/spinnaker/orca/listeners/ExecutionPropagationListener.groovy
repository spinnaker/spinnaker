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
  @Override
  void beforeExecution(Persister persister, Execution execution) {
    persister.updateStatus(execution.id, RUNNING)
    log.info("Marked ${execution.id} as $RUNNING (beforeJob)");
  }

  @Override
  void afterExecution(Persister persister,
                      Execution execution,
                      ExecutionStatus executionStatus,
                      boolean wasSuccessful) {
    if (persister.isCanceled(execution.id) && executionStatus != TERMINAL) {
      executionStatus = CANCELED
    }

    if (executionStatus in [STOPPED, SKIPPED, FAILED_CONTINUE]) {
      executionStatus = SUCCEEDED
    }

    if (!executionStatus) {
      executionStatus = TERMINAL
    }

    def failedStages = execution.stages.findAll { it.status.complete && ![SUCCEEDED, SKIPPED].contains(it.status) }
    if (failedStages.any { it.context.completeOtherBranchesThenFail }) {
      executionStatus = TERMINAL
    }

    persister.updateStatus(execution.id, executionStatus)
    log.info("Marked ${execution.id} as ${executionStatus} (afterJob)")
  }
}
