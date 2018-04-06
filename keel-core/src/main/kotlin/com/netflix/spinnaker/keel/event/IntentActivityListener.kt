/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.keel.event

import com.netflix.spinnaker.keel.IntentActivityRepository
import com.netflix.spinnaker.keel.IntentChangeAction.DELETE
import com.netflix.spinnaker.keel.IntentChangeAction.UPSERT
import com.netflix.spinnaker.keel.IntentChangeRecord
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class IntentActivityListener(
  private val intentActivityRepository: IntentActivityRepository
) {

  @EventListener(AfterIntentUpsertEvent::class, AfterIntentDeleteEvent::class)
  fun recordUpsert(event: IntentAwareEvent) {
    // TODO rz - actor needs to be pulled out of AuthenticatedRequest
    intentActivityRepository.record(IntentChangeRecord(
      event.intent.id(),
      action = if (event is AfterIntentUpsertEvent) UPSERT else DELETE,
      actor = "TODO: unknown",
      value = event.intent
    ))
  }
}
