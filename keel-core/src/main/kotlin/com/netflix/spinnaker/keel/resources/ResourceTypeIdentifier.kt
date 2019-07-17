package com.netflix.spinnaker.keel.resources

import com.netflix.spinnaker.keel.api.ApiVersion

@FunctionalInterface
interface ResourceTypeIdentifier {
  fun identify(apiVersion: ApiVersion, kind: String): Class<*>
}
