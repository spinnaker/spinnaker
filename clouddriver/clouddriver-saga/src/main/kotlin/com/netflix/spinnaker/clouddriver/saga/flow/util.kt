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
package com.netflix.spinnaker.clouddriver.saga.flow

import com.netflix.spinnaker.clouddriver.saga.SagaCommand
import com.netflix.spinnaker.clouddriver.saga.exceptions.SagaSystemException
import com.netflix.spinnaker.clouddriver.saga.getStepCommandName
import org.springframework.core.ResolvableType

/**
 * Derives a [SagaCommand] name from a [SagaFlow.ActionStep].
 */
internal fun convertActionStepToCommandName(step: SagaFlow.ActionStep): String =
  getStepCommandName(convertActionStepToCommandClass(step))

/**
 * Derives a [SagaCommand] Class from a [SagaFlow.ActionStep].
 */
internal fun convertActionStepToCommandClass(step: SagaFlow.ActionStep): Class<SagaCommand> {
  val actionType = ResolvableType.forClass(step.action)
    .also { it.resolve() }

  val commandType = actionType.interfaces
    .find { SagaAction::class.java.isAssignableFrom(it.rawClass!!) }
    ?.getGeneric(0)
    ?: throw SagaSystemException("Could not resolve SagaCommand type from ActionStep: $step")

  @Suppress("UNCHECKED_CAST")
  return commandType.rawClass as Class<SagaCommand>
}
