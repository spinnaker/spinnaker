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

import com.google.common.annotations.Beta
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import java.util.function.Consumer
import java.util.function.Predicate

/**
 * A high-level DSL to help build and visualize the workflow that a [Saga] will take towards completion.
 *
 * The simplest [Saga] is one that has a single [SagaAction]. A [SagaCompletionHandler] is optional.
 */
@Beta
class SagaFlow {

  internal val steps: MutableList<Step> = mutableListOf()
  internal var completionHandler: Class<out SagaCompletionHandler<*>>? = null

  /**
   * An action to take next.
   */
  fun then(action: Class<out SagaAction<*>>): SagaFlow {
    steps.add(ActionStep(action))
    return this
  }

  /**
   * Define a conditional branch.
   *
   * The condition is evaluated at runtime.
   *
   * @param condition The [Predicate] that will evaluate whether or not to branch logic
   * @param builder The nested [SagaFlow] used to define the branched steps, will only be called if [condition] is true
   */
  fun on(condition: Class<out Predicate<Saga>>, builder: (SagaFlow) -> Unit): SagaFlow {
    steps.add(ConditionStep(condition, SagaFlow().also(builder)))
    return this
  }

  /**
   * Java-compatible interface.
   */
  fun on(condition: Class<out Predicate<Saga>>, builder: Consumer<SagaFlow>): SagaFlow {
    steps.add(ConditionStep(condition, SagaFlow().also { builder.accept(this) }))
    return this
  }

  /**
   * An optional [SagaCompletionHandler].
   *
   * @param handler The [SagaCompletionHandler] to invoke on completion
   */
  fun completionHandler(handler: Class<out SagaCompletionHandler<*>>): SagaFlow {
    completionHandler = handler
    return this
  }

  interface Step
  inner class ActionStep(val action: Class<out SagaAction<*>>) : Step
  inner class ConditionStep(val predicate: Class<out Predicate<Saga>>, val nestedBuilder: SagaFlow) : Step
}
