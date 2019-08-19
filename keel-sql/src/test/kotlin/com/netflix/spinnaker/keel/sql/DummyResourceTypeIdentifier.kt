package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Named
import com.netflix.spinnaker.keel.resources.ResourceTypeIdentifier
import com.netflix.spinnaker.keel.test.DummyResourceSpec

internal object DummyResourceTypeIdentifier : ResourceTypeIdentifier {
  override fun identify(apiVersion: ApiVersion, kind: String): Class<out Named> = DummyResourceSpec::class.java
}
