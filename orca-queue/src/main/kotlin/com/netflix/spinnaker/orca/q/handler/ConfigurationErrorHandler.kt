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

package com.netflix.spinnaker.orca.q.handler

import com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.ConfigurationError
import com.netflix.spinnaker.orca.q.InvalidExecutionId
import com.netflix.spinnaker.q.Queue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ConfigurationErrorHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository
) : OrcaMessageHandler<ConfigurationError> {

  override val messageType = ConfigurationError::class.java

  private val log = LoggerFactory.getLogger(javaClass)

  override fun handle(message: ConfigurationError) {
    when (message) {
      is InvalidExecutionId ->
        log.error("No such ${message.executionType} ${message.executionId} for ${message.application}")
      else -> {
        log.error("${message.javaClass.simpleName} for ${message.executionType} ${message.executionId} for ${message.application}")
        repository.updateStatus(message.executionId, TERMINAL)
      }
    }
  }
}
