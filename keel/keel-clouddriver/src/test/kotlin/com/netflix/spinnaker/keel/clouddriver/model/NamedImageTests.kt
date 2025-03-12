package com.netflix.spinnaker.keel.clouddriver.model

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class NamedImageTests : JUnit5Minutests {
  fun tests() = rootContext<NamedImage> {
    context("hasAppVersion tests") {

      // Helper function to make a NamedImage fixture
      fun makeNamedImage(tagsByImageId: Map<String, Map<String, String?>?>) =
        NamedImage(
          imageName = "name",
          attributes = mapOf("foo" to "bar"),
          accounts = setOf("test"),
          amis = mapOf("us-east-1" to listOf("ami-12345")),
          tagsByImageId = tagsByImageId
        )

      context("properly populated tagsByImageId") {
        fixture {
          makeNamedImage(
            tagsByImageId = mapOf(
              "ami-abc123" to
                mapOf(
                  "appversion" to "foo-0.0.1-h123.abcde",
                  "base_ami_version" to "base"
                )
            )
          )
        }

        test("has an app version") {
          expectThat(hasAppVersion).isTrue()
        }
      }

      context("null tagsByImageId values") {
        fixture { makeNamedImage(tagsByImageId = mapOf("ami-abc123" to null)) }

        test("does not have an app version") {
          expectThat(hasAppVersion).isFalse()
        }
      }
      context("empty tagsByImageId") {
        fixture { makeNamedImage(tagsByImageId = emptyMap()) }

        test("does not have an app version") {
          expectThat(hasAppVersion).isFalse()
        }
      }
    }
  }
}
