package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.resources.ResourceTypeIdentifier
import com.netflix.spinnaker.keel.test.DummyLocatableResourceSpec
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.TEST_API_V1

internal object DummyResourceTypeIdentifier : ResourceTypeIdentifier {
  override fun identify(kind: ResourceKind): Class<out ResourceSpec> {
    return when (kind) {
      TEST_API_V1.qualify("locatable") -> DummyLocatableResourceSpec::class.java
      else -> DummyResourceSpec::class.java
    }
  }
}
