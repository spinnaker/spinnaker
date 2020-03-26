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
  JsonSubTypes.Type(value = ApplicationEvent::class, name = "application"),
  JsonSubTypes.Type(value = ResourceEvent::class, name = "resource")
)
abstract class PersistentEvent {
  abstract val scope: Scope
  abstract val application: String
  abstract val uid: String // The unique ID of the thing associated with the scope. Defined in sub-classes.
  abstract val timestamp: Instant
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
