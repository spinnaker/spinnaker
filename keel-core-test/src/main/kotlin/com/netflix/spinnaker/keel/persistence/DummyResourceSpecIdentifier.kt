package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.plugins.kind
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.test.DummyLocatableResourceSpec
import com.netflix.spinnaker.keel.test.DummyResourceSpec

object DummyResourceSpecIdentifier : ResourceSpecIdentifier(
  kind<DummyLocatableResourceSpec>("test/locatable@v1"),
  kind<DummyResourceSpec>("test/whatever@v1")
)
