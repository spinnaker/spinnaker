/*
 * Copyright 2017 Netflix, Inc.
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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonValue
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentSpec
import org.springframework.context.ApplicationEvent

enum class EventKind(val kind: String) {
  BEFORE_INTENT_UPSERT("beforeIntentUpsert"),
  AFTER_INTENT_UPSERT("afterIntentUpsert"),
  BEFORE_INTENT_DELETE("beforeIntentDelete"),
  AFTER_INTENT_DELETE("afterIntentDelete"),
  BEFORE_INTENT_DRYRUN("beforeIntentDryRun"),
  BEFORE_INTENT_CONVERGE("beforeIntentConverge"),
  INTENT_CONVERGE_TIMEOUT("intentConvergeTimeout"),
  INTENT_CONVERGE_NOT_FOUND("intentConvergeNotFound"),
  INTENT_CONVERGE_SUCCESS("intentConvergeSuccess"),
  INTENT_CONVERGE_FAILURE("intentConvergeFailure");

  companion object {
    @JvmStatic @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    fun fromValue(value: String): EventKind {
      return EventKind.values()
        .find { it.kind == value || it.name == value }
        ?: throw IllegalArgumentException("$value is not a valid EventKind")
    }
  }

  @JsonValue fun toValue() = kind
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "kind")
@JsonSubTypes(
  Type(AfterIntentUpsertEvent::class),
  Type(AfterIntentDeleteEvent::class),
  Type(BeforeIntentConvergeEvent::class),
  Type(IntentConvergeTimeoutEvent::class),
  Type(IntentConvergeNotFoundEvent::class),
  Type(IntentConvergeSuccessEvent::class),
  Type(IntentConvergeFailureEvent::class)
)
abstract class KeelEvent : ApplicationEvent("internal") {
  abstract val kind: EventKind
}

abstract class IntentAwareEvent() : KeelEvent() {
  abstract val intent: Intent<IntentSpec>
}

data class BeforeIntentUpsertEvent(
  override val intent: Intent<IntentSpec>
) : IntentAwareEvent() {
  override val kind = EventKind.BEFORE_INTENT_UPSERT
}

data class AfterIntentUpsertEvent(
  override val intent: Intent<IntentSpec>
) : IntentAwareEvent() {
  override val kind = EventKind.AFTER_INTENT_UPSERT
}

data class BeforeIntentDeleteEvent(
  override val intent: Intent<IntentSpec>
) : IntentAwareEvent() {
  override val kind = EventKind.BEFORE_INTENT_DELETE
}

data class AfterIntentDeleteEvent(
  override val intent: Intent<IntentSpec>
) : IntentAwareEvent() {
  override val kind = EventKind.AFTER_INTENT_DELETE
}

data class BeforeIntentDryRunEvent(
  override val intent: Intent<IntentSpec>
) : IntentAwareEvent() {
  override val kind = EventKind.BEFORE_INTENT_DRYRUN
}

data class BeforeIntentConvergeEvent(
  override val intent: Intent<IntentSpec>
) : IntentAwareEvent() {
  override val kind = EventKind.BEFORE_INTENT_CONVERGE
}

data class IntentConvergeTimeoutEvent(
  override val intent: Intent<IntentSpec>
) : IntentAwareEvent() {
  override val kind = EventKind.INTENT_CONVERGE_TIMEOUT
}

data class IntentConvergeNotFoundEvent(
  val intentId: String
) : KeelEvent() {
  override val kind = EventKind.INTENT_CONVERGE_NOT_FOUND
}

data class IntentConvergeSuccessEvent(
  override val intent: Intent<IntentSpec>,
  val orchestrations: List<String>
) : IntentAwareEvent() {
  override val kind = EventKind.INTENT_CONVERGE_SUCCESS
}

data class IntentConvergeFailureEvent(
  override val intent: Intent<IntentSpec>,
  val reason: String,
  val cause: Throwable?
) : IntentAwareEvent() {
  override val kind = EventKind.INTENT_CONVERGE_FAILURE
}
