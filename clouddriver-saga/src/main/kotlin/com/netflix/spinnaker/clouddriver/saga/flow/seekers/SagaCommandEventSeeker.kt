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

import com.netflix.spinnaker.clouddriver.saga.SagaCommand
import com.netflix.spinnaker.clouddriver.saga.flow.SagaFlow
import com.netflix.spinnaker.clouddriver.saga.flow.Seeker
import com.netflix.spinnaker.clouddriver.saga.flow.convertActionStepToCommandClass
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import org.slf4j.LoggerFactory

/**
 * Seeks the [SagaFlowIterator] index to the next incomplete, but committed [SagaCommand].
 */
internal class SagaCommandEventSeeker : Seeker {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun invoke(currentIndex: Int, steps: List<SagaFlow.Step>, saga: Saga): Int? {
    val commands = saga.getEvents().filterIsInstance<SagaCommand>()
    if (commands.isEmpty()) {
      // No commands, nothing to seek to
      return null
    }

    val lastCommand = commands.last().javaClass
    val step = steps
      .filterIsInstance<SagaFlow.ActionStep>()
      .find { convertActionStepToCommandClass(it) == lastCommand }

    if (step == null) {
      log.error("Could not find step associated with last incomplete command ($lastCommand)")
      return null
    }

    return (steps.indexOf(step)).also {
      log.debug("Suggesting to seek index to $it")
    }
  }
}
