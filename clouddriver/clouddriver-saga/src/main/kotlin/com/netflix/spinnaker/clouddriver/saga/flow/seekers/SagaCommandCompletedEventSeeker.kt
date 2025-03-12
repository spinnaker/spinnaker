/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.saga.flow.seekers

import com.netflix.spinnaker.clouddriver.saga.SagaCommandCompleted
import com.netflix.spinnaker.clouddriver.saga.flow.SagaFlow
import com.netflix.spinnaker.clouddriver.saga.flow.Seeker
import com.netflix.spinnaker.clouddriver.saga.flow.convertActionStepToCommandName
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import org.slf4j.LoggerFactory

/**
 * Seeks the [SagaFlowIterator] index to the next command following a [SagaCommandCompleted] event.
 */
internal class SagaCommandCompletedEventSeeker : Seeker {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun invoke(currentIndex: Int, steps: List<SagaFlow.Step>, saga: Saga): Int? {
    val completionEvents = saga.getEvents().filterIsInstance<SagaCommandCompleted>()
    if (completionEvents.isEmpty()) {
      // If there are no completion events, we don't need to seek at all.
      return null
    }

    val lastCompletedCommand = completionEvents.last().command
    val step = steps
      .filterIsInstance<SagaFlow.ActionStep>()
      .find { convertActionStepToCommandName(it) == lastCompletedCommand }

    if (step == null) {
      // Not the end of the world if this seeker doesn't find a correlated step, but it's definitely an error case
      log.error("Could not find step associated with last completed command ($lastCompletedCommand)")
      return null
    }

    return (steps.indexOf(step) + 1).also {
      log.debug("Suggesting to seek index to $it")
    }
  }
}
