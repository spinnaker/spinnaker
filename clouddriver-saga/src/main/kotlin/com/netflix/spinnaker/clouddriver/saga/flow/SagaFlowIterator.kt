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
import com.netflix.spinnaker.clouddriver.saga.SagaCommandCompleted
import com.netflix.spinnaker.clouddriver.saga.exceptions.SagaNotFoundException
import com.netflix.spinnaker.clouddriver.saga.exceptions.SagaSystemException
import com.netflix.spinnaker.clouddriver.saga.flow.seekers.SagaCommandCompletedEventSeeker
import com.netflix.spinnaker.clouddriver.saga.flow.seekers.SagaCommandEventSeeker
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
  private var seeked: Boolean = false

  // toList.toMutableList copies the list so while we mutate stuff, it's all internal
  private var steps = flow.steps.toList().toMutableList()

  private lateinit var latestSaga: Saga

  override fun hasNext(): Boolean {
    if (index >= steps.size) {
      return false
    }

    // The iterator needs the latest state of a saga to correctly determine in the next step to take.
    // This is kind of handy, since we can pass this newly refreshed state straight to the iterator consumer so they
    // don't need to concern themselves with that.
    latestSaga = sagaRepository.get(context.sagaName, context.sagaId)
      ?: throw SagaNotFoundException("Could not find Saga (${context.sagaName}/${context.sagaId} for flow traversal")

    // To support resuming sagas, we want to seek to the next step that has not been processed,
    // which may not be the first step.
    seekToNextStep(latestSaga)

    val nextStep = steps[index]

    // If the next step is a condition, try to wire it up from the [ApplicationContext] and run it against the latest
    // state. If the predicate is true, its nested [SagaFlow] will be injected into the current steps list, replacing
    // the condition step's location. If the condition is false, then we just remove the step from the list.
    if (nextStep is SagaFlow.ConditionStep) {
      val predicate = try {
        applicationContext.getBean(nextStep.predicate)
      } catch (e: BeansException) {
        throw SagaSystemException("Failed to create SagaFlow Predicate: ${nextStep.predicate.simpleName}", e)
      }

      // TODO(rz): Add a ConditionEvaluated event. We shouldn't be re-evaluating conditions when resuming a Saga
      val result = predicate.test(latestSaga)
      log.trace("Predicate '${predicate.javaClass.simpleName}' result: $result")
      if (result) {
        steps.addAll(index, nextStep.nestedBuilder.steps)
      }
      steps.remove(nextStep)
    }

    return index < steps.size
  }

  /**
   * Seeks the iterator to the next step that needs to be (re)started, if the saga has already begun.
   *
   * Multiple strategies are used to locate the correct index to seek to. The highest index returned from the [Seeker]
   * strategies will be used for seeking.
   *
   * TODO(rz): What if there is more than 1 of a particular command in a flow? :thinking_face: May need more metadata
   * in the [SagaCommandCompleted] event passed along...
   */
  private fun seekToNextStep(saga: Saga) {
    if (seeked) {
      // We only want to seek once
      return
    }
    seeked = true

    index = listOf(SagaCommandCompletedEventSeeker(), SagaCommandEventSeeker())
      .mapNotNull { it.invoke(index, steps, saga)?.coerceAtLeast(0) }
      .min()
      ?: index

    if (index != 0) {
      log.info("Seeking to step index $index")
    }
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

/**
 * Allows multiple strategies to be used to locate the correct starting point for the [SagaFlowIterator].
 *
 * If a Seeker cannot determine an index, null should be returned. If multiple Seekers return an index, the
 * highest value will be used.
 */
internal typealias Seeker = (currentIndex: Int, steps: List<SagaFlow.Step>, saga: Saga) -> Int?
