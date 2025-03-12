package com.netflix.spinnaker.keel.serialization

import com.fasterxml.jackson.databind.node.ObjectNode
import com.netflix.spinnaker.keel.core.api.ActionMetadata
import com.netflix.spinnaker.keel.core.api.ArtifactSummaryInEnvironment
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import java.time.Instant.now
import strikt.api.expectThat
import strikt.jackson.has
import strikt.jackson.isObject
import strikt.jackson.path

class ArtifactSummarySerializationTests : JUnit5Minutests {

  fun tests() = rootContext {
    test("a boolean property is rendered with an is* prefix") {
      val obj = ArtifactSummaryInEnvironment(
        environment = "test",
        version = "12345",
        state = "PENDING",
        pinned = ActionMetadata(at = now(), by = "HULK", comment = "HULK PIN ARTIFACT")
      )

      val tree = configuredObjectMapper()
        .valueToTree<ObjectNode>(obj)

      expectThat(tree)
        .has("pinned")
        .path("pinned").isObject()
    }
  }
}
