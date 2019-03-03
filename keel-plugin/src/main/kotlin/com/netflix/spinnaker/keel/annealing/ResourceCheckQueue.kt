package com.netflix.spinnaker.keel.annealing

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceName

interface ResourceCheckQueue {
  fun scheduleCheck(
    name: ResourceName,
    apiVersion: ApiVersion,
    kind: String
  )
}

fun ResourceCheckQueue.scheduleCheck(resource: Resource<*>) {
  with(resource) {
    scheduleCheck(metadata.name, apiVersion, kind)
  }
}
