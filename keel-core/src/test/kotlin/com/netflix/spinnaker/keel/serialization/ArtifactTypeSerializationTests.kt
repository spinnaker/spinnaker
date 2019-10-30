package com.netflix.spinnaker.keel.serialization

import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.ArtifactType
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.get
import strikt.assertions.isEqualTo

internal class ArtifactTypeSerializationTests : JUnit5Minutests {

  val mapper = configuredYamlMapper()

  fun tests() = rootContext<Unit> {
    context("custom (de)serialization") {
      test("serializes enum value to friendly name") {
        for (artifactType in ArtifactType.values()) {
          expectThat(mapper.writeValueAsString(artifactType)).isEqualTo("--- \"${artifactType.friendlyName}\"\n")
        }
      }

      test("deserializes friendly name to enum value") {
        for (artifactType in ArtifactType.values()) {
          expectThat(mapper.readValue<ArtifactType>(artifactType.friendlyName)).isEqualTo(artifactType)
        }
      }

      test("deserialization is backwards-compatible with original enum names") {
        for (artifactType in ArtifactType.values()) {
          expectThat(mapper.readValue<ArtifactType>(artifactType.name)).isEqualTo(artifactType)
        }
      }
    }
  }
}
