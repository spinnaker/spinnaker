package com.netflix.spinnaker.keel.model

import com.netflix.spinnaker.keel.api.Moniker
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.get
import strikt.assertions.isEqualTo

internal class MonikerTests : JUnit5Minutests {
  fun tests() = rootContext<Moniker> {
    context("a cluster with no stack or detail") {
      fixture { Moniker(app = "fnord") }

      test("has a cluster name that is just the application") {
        expectThat(name).isEqualTo("fnord")
      }
    }

    context("a server group with no stack or detail") {
      fixture { Moniker(app = "fnord", sequence = 5) }

      test("has a server group name that is just the application and sequence") {
        expectThat(serverGroup).isEqualTo("fnord-v005")
      }

      test("exports to orcaClusterMoniker") {
        val orcaMoniker: Map<String, Any?> = orcaClusterMoniker
        expectThat(orcaMoniker) {
          get("app").isEqualTo("fnord")
          get("sequence").isEqualTo(5)
        }
      }
    }

    context("a cluster with a stack but no detail") {
      fixture { Moniker(app = "fnord", stack = "test") }

      test("has a cluster name that is the application and stack") {
        expectThat(name).isEqualTo("fnord-test")
      }
    }

    context("a server group with a stack but no detail") {
      fixture { Moniker(app = "fnord", stack = "test", sequence = 5) }

      test("has a server group name that is the application, stack, and sequence") {
        expectThat(serverGroup).isEqualTo("fnord-test-v005")
      }
    }

    context("a cluster with both stack and detail") {
      fixture { Moniker(app = "fnord", stack = "test", detail = "foo") }

      test("has a cluster name that is the application, stack, and detail") {
        expectThat(name).isEqualTo("fnord-test-foo")
      }
    }

    context("a server group with both stack and detail") {
      fixture { Moniker(app = "fnord", stack = "test", detail = "foo", sequence = 5) }

      test("has a server group name that is the application, stack, detail, and version") {
        expectThat(serverGroup).isEqualTo("fnord-test-foo-v005")
      }
    }

    context("a cluster with no stack and a detail") {
      fixture { Moniker(app = "fnord", detail = "foo") }

      test("has a cluster name that is the application and detail") {
        expectThat(name).isEqualTo("fnord--foo")
      }
    }

    context("a server group with no stack and a detail") {
      fixture { Moniker(app = "fnord", detail = "foo", sequence = 5) }

      test("has a server group name that is the application, detail, and sequence") {
        expectThat(serverGroup).isEqualTo("fnord--foo-v005")
      }
    }
  }
}
