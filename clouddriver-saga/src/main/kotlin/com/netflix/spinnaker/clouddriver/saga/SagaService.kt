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
package com.netflix.spinnaker.clouddriver.saga

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.saga.exceptions.SagaIntegrationException
import com.netflix.spinnaker.clouddriver.saga.exceptions.SagaMissingRequiredCommandException
import com.netflix.spinnaker.clouddriver.saga.exceptions.SagaNotFoundException
import com.netflix.spinnaker.clouddriver.saga.exceptions.SagaSystemException
import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction
import com.netflix.spinnaker.clouddriver.saga.flow.SagaFlow
import com.netflix.spinnaker.clouddriver.saga.flow.SagaFlowIterator
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import com.netflix.spinnaker.clouddriver.saga.persistence.SagaRepository
import com.netflix.spinnaker.kork.annotations.Beta
import com.netflix.spinnaker.kork.exceptions.SpinnakerException
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.core.ResolvableType

/**
 * The main brains of the Saga library. Orchestrates the progression of a [Saga] until its completion.
 *
 * A [Saga] is a way of performing orchestrated distributed service transactions, and in the case of this library,
 * is implemented through the a series of log-backed "actions". A [SagaAction] is a reentrant and idempotent function
 * that changes a remote system. The results of a [SagaAction] are committed into a log so that if at any point a
 * Saga is interrupted, it may be resumed. Like all transactional systems, a [Saga] may also be rolled back if its
 * [SagaAction]s are implemented as a [CompensatingSagaAction]. A rollback is managed by consumers of the Saga
 * library and as such, there are no internal heuristics to dictate when a [Saga] will or will not be compensated.
 *
 * For every [SagaCommand], there is 0 to N [SagaAction]s. A [SagaAction] requires a [SagaCommand] which is provided
 * either by the initial request into the [SagaService], or by a predecessor [SagaAction]. A [SagaAction] can emit 0
 * to N [SagaCommand]s, as well as [SagaEvent]s. The difference between the two is that a [SagaCommand] will move
 * the progress of a [Saga] forward (or backwards if rolling back), whereas a [SagaEvent] will be published to all
 * subscribers interested in it and will not affect the workflow of a [Saga].
 *
 * ```
 * val flow = SagaFlow()
 *   .next(MyAction::class.java)
 *   .completionHandler(MyCompletionHandler::class.java)
 *
 * val result = sagaService.applyBlocking(flow, DoMyAction())
 * ```
 */
@Beta
class SagaService(
  private val sagaRepository: SagaRepository,
  private val registry: Registry
) : ApplicationContextAware {

  private lateinit var applicationContext: ApplicationContext

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val actionInvocationsId = registry.createId("sagas.actions.invocations")

  fun <T> applyBlocking(sagaName: String, sagaId: String, flow: SagaFlow, startingCommand: SagaCommand): T? {
    val initialSaga = initializeSaga(startingCommand, sagaName, sagaId)

    log.info("Applying saga: ${initialSaga.name}/${initialSaga.id}")

    if (initialSaga.isComplete()) {
      log.info("Saga already complete, exiting early: ${initialSaga.name}/${initialSaga.id}")
      return invokeCompletionHandler(initialSaga, flow)
    }

    // TODO(rz): Validate that the startingCommand == the originating startingCommand payload?

    SagaFlowIterator(sagaRepository, applicationContext, initialSaga, flow).forEach { flowState ->
      val saga = flowState.saga
      val action = flowState.action

      log.debug("Applying saga action ${action.javaClass.simpleName} for ${saga.name}/${saga.id}")

      val requiredCommand: Class<SagaCommand> = getRequiredCommand(action)
      if (!saga.completed(requiredCommand)) {
        val stepCommand = saga.getNextCommand(requiredCommand)
          ?: throw SagaMissingRequiredCommandException("Missing required command ${requiredCommand.simpleName}")

        val result = try {
          action.apply(stepCommand, saga).also {
            registry
              .counter(actionInvocationsId.withTags("result", "success", "action", action.javaClass.simpleName))
              .increment()
          }
        } catch (e: Exception) {
          // TODO(rz): Add SagaAction.recover()
          val handledException = invokeExceptionHandler(flow, e)

          log.error(
            "Encountered error while applying action '${action.javaClass.simpleName}' on ${saga.name}/${saga.id}",
            handledException
          )

          saga.addEvent(
            SagaActionErrorOccurred(
              actionName = action.javaClass.simpleName,
              error = handledException,
              retryable = when (handledException) {
                is SpinnakerException -> handledException.retryable ?: false
                else -> false
              }
            )
          )
          sagaRepository.save(saga)

          registry
            .counter(actionInvocationsId.withTags("result", "failure", "action", action.javaClass.simpleName))
            .increment()

          log.error("Failed to apply action ${action.javaClass.simpleName} for ${saga.name}/${saga.id}")
          throw handledException
        }

        saga.setSequence(stepCommand.getMetadata().sequence)

        val newEvents: MutableList<SagaEvent> = result.events.toMutableList().also {
          it.add(SagaCommandCompleted(getStepCommandName(stepCommand)))
        }

        val nextCommand = result.nextCommand
        if (nextCommand == null) {
          if (flowState.hasMoreSteps() && !saga.hasUnappliedCommands()) {
            saga.complete(false)
            sagaRepository.save(saga, listOf())
            throw SagaIntegrationException("Result did not return a nextCommand value, but flow has more steps defined")
          }
          saga.complete(true)
        } else {
          // TODO(rz): Would be nice to flag commands that are optional so its clearer in the event log
          if (nextCommand is ManyCommands) {
            newEvents.addAll(nextCommand.commands)
          } else {
            newEvents.add(nextCommand)
          }
        }

        sagaRepository.save(saga, newEvents)
      }
    }

    return invokeCompletionHandler(initialSaga, flow)
  }

  private fun initializeSaga(command: SagaCommand, sagaName: String, sagaId: String): Saga {
    return sagaRepository.get(sagaName, sagaId)
      ?: Saga(sagaName, sagaId)
        .also {
          log.debug("Initializing new saga: $sagaName/$sagaId")
          it.addEvent(command)
          sagaRepository.save(it)
        }
  }

  private fun <T> invokeCompletionHandler(saga: Saga, flow: SagaFlow): T? {
    return flow.completionHandler
      ?.let { completionHandler ->
        val handler = applicationContext.getBean(completionHandler)
        val result = sagaRepository.get(saga.name, saga.id)
          ?.let { handler.handle(it) }
          ?: throw SagaNotFoundException("Could not find Saga to complete by ${saga.name}/${saga.id}")

        // TODO(rz): Haha... :(
        try {
          @Suppress("UNCHECKED_CAST")
          return result as T?
        } catch (e: ClassCastException) {
          throw SagaIntegrationException("The completion handler is incompatible with the expected return type", e)
        }
      }
  }

  private fun invokeExceptionHandler(flow: SagaFlow, exception: Exception): Exception {
    flow.exceptionHandler?.let { exceptionHandler ->
      val handler = applicationContext.getBean(exceptionHandler)
      return handler.handle(exception)
    }
    return exception
  }

  private fun getRequiredCommand(action: SagaAction<SagaCommand>): Class<SagaCommand> {
    val actionType = ResolvableType.forClass(SagaAction::class.java, action.javaClass)
    actionType.resolve()

    val commandType = actionType.getGeneric(0)
    commandType.resolve()

    val rawClass = commandType.rawClass!!
    if (SagaCommand::class.java.isAssignableFrom(rawClass)) {
      @Suppress("UNCHECKED_CAST")
      return rawClass as Class<SagaCommand>
    }
    throw SagaSystemException("Resolved next action is not a SagaCommand: ${rawClass.simpleName}")
  }

  override fun setApplicationContext(applicationContext: ApplicationContext) {
    this.applicationContext = applicationContext
  }
}
