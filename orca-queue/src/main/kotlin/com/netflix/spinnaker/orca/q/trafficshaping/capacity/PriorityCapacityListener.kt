/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.q.trafficshaping.capacity

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.events.ExecutionComplete
import com.netflix.spinnaker.orca.events.ExecutionEvent
import com.netflix.spinnaker.orca.events.ExecutionStarted
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationListener

class PriorityCapacityListener(
  private val priorityCapacityRepository: PriorityCapacityRepository,
  private val prioritizationStrategy: PrioritizationStrategy
) : ApplicationListener<ExecutionEvent> {

  private val log: Logger = LoggerFactory.getLogger(javaClass)

  override fun onApplicationEvent(event: ExecutionEvent) {
    when (event) {
      is ExecutionStarted -> priorityCapacityRepository.incrementExecutions(getPriority(event))
      is ExecutionComplete -> priorityCapacityRepository.decrementExecutions(getPriority(event))
    }
  }

  private fun getPriority(event: ExecutionEvent): Priority {
    try {
      return prioritizationStrategy.getPriority(event)
    } catch (e: Exception) {
      log.error("Could not determine priority of execution, assigning MEDIUM (message: $event)", e)
      return Priority.MEDIUM
    }
  }
}
