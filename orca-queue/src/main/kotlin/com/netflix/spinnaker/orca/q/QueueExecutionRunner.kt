/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.q

import com.netflix.spinnaker.orca.pipeline.ExecutionRunner
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionEngine
import com.netflix.spinnaker.security.AuthenticatedRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component open class QueueExecutionRunner @Autowired constructor(
  private val queue: Queue
) : ExecutionRunner {
  override fun engine(): ExecutionEngine = ExecutionEngine.v3

  override fun <T : Execution<T>> start(execution: T) =
    queue.push(StartExecution(execution))

  override fun <T : Execution<T>> restart(execution: T, stageId: String) {
    queue.push(RestartStage(execution, stageId, AuthenticatedRequest.getSpinnakerUser().orElse(null)))
  }

  override fun <T : Execution<T>> unpause(execution: T) {
    queue.push(ResumeExecution(execution))
  }

  override fun <T : Execution<T>> cancel(execution: T, user: String, reason: String?) {
    queue.push(CancelExecution(execution, user, reason))
  }
}
