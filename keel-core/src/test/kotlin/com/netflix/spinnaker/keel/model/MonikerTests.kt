package com.netflix.spinnaker.keel.model

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal class MonikerTests : JUnit5Minutests {
  fun tests() = rootContext<Moniker> {
    context("a cluster with no stack or detail") {
      fixture { Moniker(app = "fnord") }

      test("has a cluster name that is just the application") {
        expectThat(name).isEqualTo(app)
      }
    }

    context("a server group with no stack or detail") {
      fixture { Moniker(app = "fnord", sequence = "069") }

      test("has a server group name that is just the application and sequence") {
        expectThat(serverGroup).isEqualTo("$app-v$sequence")
      }
    }

    context("a cluster with a stack but no detail") {
      fixture { Moniker(app = "fnord", stack = "test") }

      test("has a cluster name that is the application and stack") {
        expectThat(name).isEqualTo("$app-$stack")
      }
    }

    context("a server group with a stack but no detail") {
      fixture { Moniker(app = "fnord", stack = "test", sequence = "069") }

      test("has a server group name that is the application, stack, and sequence") {
        expectThat(serverGroup).isEqualTo("$app-$stack-v$sequence")
      }
    }

    context("a cluster with both stack and detail") {
      fixture { Moniker(app = "fnord", stack = "test", detail = "foo") }

      test("has a cluster name that is the application, stack, and detail") {
        expectThat(name).isEqualTo("$app-$stack-$detail")
      }
    }

    context("a server group with both stack and detail") {
      fixture { Moniker(app = "fnord", stack = "test", detail = "foo", sequence = "069") }

      test("has a server group name that is the application, stack, detail, and version") {
        expectThat(serverGroup).isEqualTo("$app-$stack-$detail-v$sequence")
      }
    }

    context("a cluster with no stack and a detail") {
      fixture { Moniker(app = "fnord", detail = "foo") }

      test("has a cluster name that is the application and detail") {
        expectThat(name).isEqualTo("$app--$detail")
      }
    }

    context("a server group with no stack and a detail") {
      fixture { Moniker(app = "fnord", detail = "foo", sequence = "069") }

      test("has a server group name that is the application, detail, and sequence") {
        expectThat(serverGroup).isEqualTo("$app--$detail-v$sequence")
      }
    }
  }
}
