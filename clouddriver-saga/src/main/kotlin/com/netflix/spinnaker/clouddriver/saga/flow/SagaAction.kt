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
import com.netflix.spinnaker.clouddriver.saga.SagaCommand
import com.netflix.spinnaker.clouddriver.saga.SagaEvent
import com.netflix.spinnaker.clouddriver.saga.models.Saga

/**
 * A discrete action in a [Saga].
 *
 * When the Saga reaches this action, it will refresh the latest [Saga] context and persist a snapshot marker
 * to the event store once the action has been applied successfully.
 *
 * A [SagaAction] must be written such that it is reentrant and idempotent in the case of client-invoked
 * retries (due to internal or downstream system failure). Upon completion of an Action, it can emit 0 to N
 * [SagaCommand]s and 0 to N [SagaEvent]s. Only [SagaCommand]s will be able to move a [Saga]'s progress forward,
 * whereas a [SagaEvent] will just notify interested parties of changes within the system.
 */
@Beta
interface SagaAction<in T : SagaCommand> {
  /**
   * @param command The input [SagaCommand] to act on
   * @param saga The latest [Saga] state
   */
  fun apply(command: T, saga: Saga): Result

  /**
   * @property nextCommand The next [SagaCommand] to run, if any. [ManyCommands] can be used to emit more
   *                       than one command if necessary
   * @property events A list of events to publish to subscribers
   */
  data class Result(
    val nextCommand: SagaCommand?,
    val events: List<SagaEvent>
  ) {
    constructor() : this(null)
    constructor(nextCommand: SagaCommand?) : this(nextCommand, listOf())
  }
}
