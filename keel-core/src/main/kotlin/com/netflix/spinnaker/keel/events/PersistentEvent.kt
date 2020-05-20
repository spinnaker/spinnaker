package com.netflix.spinnaker.keel.events

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
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
  JsonSubTypes.Type(value = ApplicationEvent::class),
  JsonSubTypes.Type(value = ResourceEvent::class)
)
abstract class PersistentEvent {
  abstract val scope: Scope
  abstract val application: String
  abstract val ref: String // The unique ID of the thing associated with the scope. Defined in sub-classes.
  abstract val timestamp: Instant
  abstract val triggeredBy: String?
  @JsonIgnore
  open val ignoreRepeatedInHistory: Boolean = false

  companion object {
    val clock: Clock = Clock.systemUTC()
  }

  enum class Scope {
    @JsonProperty("resource") RESOURCE,
    @JsonProperty("application") APPLICATION
  }
}

/**
 * Common interface implemented by all [ResourceEvent]s and certain [ApplicationEvent]s that affect all the
 * application's resources, such as pausing and resuming.
 */
@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  property = "type",
  include = JsonTypeInfo.As.PROPERTY
)
@JsonSubTypes(
  JsonSubTypes.Type(value = ResourceEvent::class),
  JsonSubTypes.Type(value = ApplicationActuationPaused::class),
  JsonSubTypes.Type(value = ApplicationActuationResumed::class)
)
interface ResourceHistoryEvent {
  val scope: PersistentEvent.Scope
  val ref: String // the resource ID or application name
  val ignoreRepeatedInHistory: Boolean
  val timestamp: Instant
}
