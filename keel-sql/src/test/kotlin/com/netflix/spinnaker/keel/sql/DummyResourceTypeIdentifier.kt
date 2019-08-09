package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.resources.ResourceTypeIdentifier

internal object DummyResourceTypeIdentifier : ResourceTypeIdentifier {
  override fun identify(apiVersion: ApiVersion, kind: String): Class<*> = Map::class.java
}
