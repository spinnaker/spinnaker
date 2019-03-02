package com.netflix.spinnaker.keel.annealing

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceName

/**
 * An event indicating that the current state of a resource should be compared against the desired
 * state and actuation started if a delta is detected.
 */
data class ResourceCheckEvent(
  val name: ResourceName,
  val apiVersion: ApiVersion,
  val kind: String
) {
  constructor(resource: Resource<*>) : this(
    resource.metadata.name,
    resource.apiVersion,
    resource.kind
  )
}
