package com.netflix.spinnaker.keel.persistence

import java.time.Instant

data class ResourceStateHistoryEntry(
  val state: ResourceState,
  val timestamp: Instant
)
