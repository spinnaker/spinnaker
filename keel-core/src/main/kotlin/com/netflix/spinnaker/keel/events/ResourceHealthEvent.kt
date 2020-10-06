package com.netflix.spinnaker.keel.events

import com.netflix.spinnaker.keel.api.Resource

/**
 * An event emitted when we calculate resource health
 */
data class ResourceHealthEvent(
  val resource: Resource<*>,
  val healthy: Boolean = true
)
