package com.netflix.spinnaker.keel.resources

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Named

@FunctionalInterface
interface ResourceTypeIdentifier {
  fun identify(apiVersion: ApiVersion, kind: String): Class<out Named>
}
