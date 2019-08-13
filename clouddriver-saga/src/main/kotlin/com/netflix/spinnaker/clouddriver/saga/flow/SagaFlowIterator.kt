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
import com.netflix.spinnaker.clouddriver.saga.exceptions.SagaNotFoundException
import com.netflix.spinnaker.clouddriver.saga.exceptions.SagaSystemException
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import com.netflix.spinnaker.clouddriver.saga.persistence.SagaRepository
import com.netflix.spinnaker.kork.exceptions.SystemException
import org.slf4j.LoggerFactory
import org.springframework.beans.BeansException
import org.springframework.context.ApplicationContext

/**
 * This iterator is responsible for refreshing the [Saga] state, flattening branch logic and hydrating a [SagaFlow]
 * with rollback commands if necessary.
 *
 * TODO(rz): add rollback direction
 *
 * @param sagaRepository The [SagaRepository] to refresh [Saga] state with
 * @param applicationContext The Spring [ApplicationContext] used to autowire flow steps
 * @param saga The [Saga] execution that is being applied
 * @param flow The [SagaFlow] being iterated
 */
class SagaFlowIterator(
  private val sagaRepository: SagaRepository,
  private val applicationContext: ApplicationContext,
  saga: Saga,
  flow: SagaFlow
) : Iterator<SagaFlowIterator.IteratorState> {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val context = Context(saga.name, saga.id)

  private var index: Int = 0

  // toList.toMutableList copies the list so while we mutate stuff, it's all internal
  private var steps = flow.steps.toList().toMutableList()

  private lateinit var latestSaga: Saga

  override fun hasNext(): Boolean {
    if (index >= steps.size) {
      return false
    }

    latestSaga = sagaRepository.get(context.sagaName, context.sagaId)
      ?: throw SagaNotFoundException("Could not find Saga (${context.sagaName}/${context.sagaId} for flow traversal")

    val nextStep = steps[index]
    if (nextStep is SagaFlow.ConditionStep) {
      val predicate = try {
        applicationContext.getBean(nextStep.predicate)
      } catch (e: BeansException) {
        throw SagaSystemException("Failed to create SagaFlow Predicate: ${nextStep.predicate.simpleName}", e)
      }

      val result = predicate.test(latestSaga)
      log.trace("Predicate '${predicate.javaClass.simpleName}' result: $result")
      if (result) {
        steps.addAll(index, nextStep.nestedBuilder.steps)
      }
      steps.remove(nextStep)
    }

    return index < steps.size
  }

  override fun next(): IteratorState {
    val step = steps[index]
    if (step !is SagaFlow.ActionStep) {
      // If this is thrown, it indicates a bug in the hasNext logic
      throw SystemException("step must be an action: $step")
    }
    index += 1

    val action = try {
      applicationContext.getBean(step.action)
    } catch (e: BeansException) {
      throw SagaSystemException("Failed to create SagaAction: ${step.action.simpleName}", e)
    }

    @Suppress("UNCHECKED_CAST")
    return IteratorState(
      saga = latestSaga,
      action = action as SagaAction<SagaCommand>,
      iterator = this
    )
  }

  /**
   * Encapsulates multiple values for the current iterator item.
   *
   * @param saga The refreshed [Saga] state
   * @param action The actual [SagaAction]
   * @param iterator This iterator
   */
  data class IteratorState(
    val saga: Saga,
    val action: SagaAction<SagaCommand>,
    private val iterator: SagaFlowIterator
  ) {

    /**
     * @return Whether or not there are more flow steps after this item. This may evaluate to true if the next
     * step is a condition, but there may not be another [SagaAction].
     */
    fun hasMoreSteps(): Boolean {
      return iterator.hasNext()
    }
  }

  private data class Context(
    val sagaName: String,
    val sagaId: String
  )
}
