package com.netflix.spinnaker.keel.jackson

import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff.Companion.mapper
import com.netflix.spinnaker.keel.test.configuredTestObjectMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.containsKey
import strikt.assertions.isFailure
import strikt.assertions.isSuccess

internal class DeliveryArtifactTests : JUnit5Minutests {
  val debianArtifact =
    """
      {
        "name": "fnord",
        "type": "deb",
        "deliveryConfigName": "my-delivery-config",
        "vmOptions": {
          "baseLabel": "RELEASE",
          "baseOs": "bionic",
          "storeType": "EBS",
          "regions": [
            "ap-south-1"
          ]
        }
      }
    """.trimIndent()

  val dockerArtifact =
    """
      {
        "name": "fnord/blah",
        "type": "docker",
        "deliveryConfigName": "my-delivery-config",
        "tagVersionStrategy": "semver-job-commit-by-job"
      }
    """.trimIndent()

  val slashyDockerArtifact =
    """
      {
        "name": "fnord/is/cool",
        "type": "docker",
        "deliveryConfigName": "my-delivery-config",
        "tagVersionStrategy": "semver-job-commit-by-job"
      }
    """.trimIndent()

  fun tests() = rootContext {
    fixture {
      Fixture(slashyDockerArtifact)
    }

    context("docker name validation") {
      test("rejects slashy") {
        expectCatching { mapper.readValue<DeliveryArtifact>(slashyDockerArtifact) }
          .isFailure()
      }
    }

    mapOf(
      debianArtifact to DebianArtifact::class.java,
      dockerArtifact to DockerArtifact::class.java
    ).forEach { (json, type) ->
      derivedContext<Fixture>(type.simpleName) {
        fixture {
          Fixture(json)
        }

        context("deserialization") {
          test("works") {
            expectCatching { mapper.readValue<DeliveryArtifact>(json) }
              .isSuccess()
          }

          derivedContext<Map<String, Any?>>("serialization") {
            deriveFixture {
              mapper.readValue<DeliveryArtifact>(json)
                .let { mapper.convertValue(it) }
            }

            test("ignores deliveryConfigName") {
              expectThat(this)
                .not()
                .containsKey(DeliveryArtifact::deliveryConfigName.name)
            }

            test("ignores versioningStrategy") {
              expectThat(this)
                .not()
                .containsKey(DeliveryArtifact::versioningStrategy.name)
            }
          }
        }
      }
    }
  }
}

private data class Fixture(
  val json: String
) {
  val mapper = configuredTestObjectMapper()
}
