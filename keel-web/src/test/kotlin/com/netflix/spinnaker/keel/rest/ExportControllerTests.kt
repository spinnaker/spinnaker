package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.test.DummyResourceHandlerV1
import com.netflix.spinnaker.keel.test.DummyResourceHandlerV2
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal class ExportControllerTests : JUnit5Minutests {
  class Fixture {
    val subject = ExportController(
      handlers = listOf(DummyResourceHandlerV1, DummyResourceHandlerV2),
      cloudDriverCache = mockk(relaxed = true),
      exportService = mockk(relaxed = true)
    )
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("parsing a resource kind from the cloud provider (group) and type (unqualified kind)") {
      test("returns the kind of the latest supported version") {
        expectThat(subject.parseKind("test", "whatever"))
          .isEqualTo(ResourceKind("test", "whatever", "2"))
      }
    }

    context("parsing a resource from the type with a version included") {
      test("returns the kind with the specified version") {
        expectThat(subject.parseKind("test", "whatever@v1"))
          .isEqualTo(ResourceKind("test", "whatever", "1"))
      }
    }
  }
}
