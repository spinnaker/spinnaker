package com.netflix.spinnaker.keel.resources

import com.netflix.spinnaker.keel.api.ResourceSpec

@FunctionalInterface
interface ResourceTypeIdentifier {
  fun identify(apiVersion: String, kind: String): Class<out ResourceSpec>
}
