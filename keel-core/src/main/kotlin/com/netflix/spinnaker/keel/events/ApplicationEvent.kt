package com.netflix.spinnaker.keel.events

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Clock
import java.time.Instant

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  property = "type",
  include = JsonTypeInfo.As.PROPERTY
)
@JsonSubTypes(
  JsonSubTypes.Type(value = ApplicationActuationPaused::class, name = "ApplicationActuationPaused"),
  JsonSubTypes.Type(value = ApplicationActuationResumed::class, name = "ApplicationActuationResumed")
)
sealed class ApplicationEvent : PersistentEvent() {
  @JsonIgnore
  override val scope = Scope.APPLICATION

  override val uid: String
    get() = application
}

/**
 * Actuation at the application level has been paused.
 *
 * @property reason The reason why actuation was paused.
 */
data class ApplicationActuationPaused(
  override val application: String,
  val reason: String?,
  override val timestamp: Instant
) : ApplicationEvent() {
  @JsonIgnore
  override val ignoreRepeatedInHistory = true

  constructor(application: String, reason: String? = null, clock: Clock = Companion.clock) : this(
    application,
    reason,
    clock.instant()
  )
}

/**
 * Actuation at the application level has resumed.
 */
data class ApplicationActuationResumed(
  override val application: String,
  val reason: String?,
  override val timestamp: Instant
) : ApplicationEvent() {
  @JsonIgnore override val ignoreRepeatedInHistory = true

  constructor(application: String, reason: String? = null, clock: Clock = Companion.clock) : this(
    application,
    reason,
    clock.instant()
  )
}
