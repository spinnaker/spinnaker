package com.netflix.spinnaker.keel.resources

import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceSpec

@FunctionalInterface
interface ResourceTypeIdentifier {
  fun identify(kind: ResourceKind): Class<out ResourceSpec>
}
