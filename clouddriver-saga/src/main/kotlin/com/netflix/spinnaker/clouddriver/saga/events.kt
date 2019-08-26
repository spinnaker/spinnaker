/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.saga

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonTypeName
import com.netflix.spinnaker.clouddriver.event.SpinnakerEvent
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import com.netflix.spinnaker.kork.exceptions.SpinnakerException

/**
 * Root event type for [Saga]s.
 *
 * @property sagaName Alias for [aggregateType]
 * @property sagaId Alias for [aggregateId]
 */
abstract class SagaEvent : SpinnakerEvent() {
  val sagaName
    @JsonIgnore get() = aggregateType

  val sagaId
    @JsonIgnore get() = aggregateId
}

/**
 * Emitted whenever a [Saga] is saved.
 *
 * This event does not attempt to find a difference in state, trading off persistence verbosity for a little bit
 * of a simpler implementation.
 *
 * @param saga The [Saga]'s newly saved state
 */
@JsonTypeName("sagaSaved")
class SagaSaved(
  val saga: Saga
) : SagaEvent()

/**
 * Emitted whenever an internal error has occurred while applying a [Saga].
 *
 * @param reason A human-readable cause for the error
 * @param error The Exception (if any) that caused the error condition
 * @param retryable Flags whether or not this error is recoverable
 * @param data Additional data that can help with diagnostics of the error
 */
@JsonTypeName("sagaInternalErrorOccurred")
class SagaInternalErrorOccurred(
  val reason: String,
  val error: Exception? = null,
  val retryable: Boolean = true,
  val data: Map<String, String> = mapOf()
) : SagaEvent()

/**
 * Emitted whenever an error has occurred within a [SagaAction] while applying a [Saga].
 *
 * @param actionName The Java simpleName of the handler
 * @param error The Exception that caused the error condition
 * @param retryable Flags whether or not this error is recoverable
 */
@JsonTypeName("sagaActionErrorOccurred")
class SagaActionErrorOccurred(
  val actionName: String,
  val error: Exception,
  val retryable: Boolean
) : SagaEvent()

/**
 * Informational log that can be added to a [Saga] for end-user feedback, as well as operational insight.
 * This is a direct tie-in for the Kato Task Status concept with some additional bells and whistles.
 *
 * @param message A tuple message that allows passing end-user- and operator-focused messages
 * @param diagnostics Additional metadata that can help provide context to the message
 */
@JsonTypeName("sagaLogAppended")
class SagaLogAppended(
  val message: Message,
  val diagnostics: Diagnostics? = null
) : SagaEvent() {

  /**
   * @param user An end-user friendly message
   * @param system An operator friendly message
   */
  data class Message(
    val user: String? = null,
    val system: String? = null
  )

  /**
   * @param error An error, if one exists. This must be a [SpinnakerException] to provide retryable metadata
   * @param data Additional metadata
   */
  data class Diagnostics(
    val error: SpinnakerException? = null,
    val data: Map<String, String> = mapOf()
  )
}

/**
 * Emitted when all actions for a [Saga] have been applied.
 */
@JsonTypeName("sagaCompleted")
class SagaCompleted(
  val success: Boolean
) : SagaEvent()

/**
 * Emitted when a [Saga] enters a rollback state.
 */
@JsonTypeName("sagaRollbackStarted")
class SagaRollbackStarted : SagaEvent()

/**
 * Emitted when all rollback actions for a [Saga] have been applied.
 */
@JsonTypeName("sagaRollbackCompleted")
class SagaRollbackCompleted : SagaEvent()

/**
 * The root event type for all mutating [Saga] operations.
 */
abstract class SagaCommand : SagaEvent()

/**
 * The root event type for all [Saga] rollback operations.
 */
abstract class SagaRollbackCommand : SagaCommand()

/**
 * Marker event for recording that the work associated with a particular [SagaCommand] event has been completed.
 *
 * @param command The [SagaCommand] name
 */
@JsonTypeName("sagaCommandCompleted")
class SagaCommandCompleted(
  val command: String
) : SagaEvent() {

  fun matches(candidateCommand: Class<out SagaCommand>): Boolean =
    candidateCommand.getAnnotation(JsonTypeName::class.java)?.value == command
}

/**
 * A [SagaCommand] wrapper for [SagaAction]s that need to return more than one [SagaCommand].
 *
 * This event is unwrapped prior to being added to the event log; so all [SagaCommand]s defined within this
 * wrapper will show up as their own distinct log entries.
 */
class ManyCommands(
  command1: SagaCommand,
  vararg extraCommands: SagaCommand
) : SagaCommand() {
  val commands = listOf(command1).plus(extraCommands)
}
