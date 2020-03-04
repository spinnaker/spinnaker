package com.netflix.spinnaker.keel.events

import java.time.Clock
import java.time.Instant

abstract class PersistentEvent {
  abstract val scope: Scope
  abstract val application: String
  abstract val uid: String // The unique ID of the thing associated with the scope. Defined in sub-classes.
  abstract val timestamp: Instant
  open val ignoreRepeatedInHistory: Boolean = false

  companion object {
    val clock: Clock = Clock.systemDefaultZone()
  }

  enum class Scope {
    RESOURCE,
    APPLICATION
  }
}
