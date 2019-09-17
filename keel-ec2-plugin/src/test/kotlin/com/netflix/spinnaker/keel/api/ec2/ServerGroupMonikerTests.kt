package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.model.Moniker
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal class ServerGroupMonikerTests : JUnit5Minutests {
  fun tests() = rootContext<Moniker> {
    context("a server group with no stack or detail") {
      fixture { Moniker(app = "fnord") }

      test("has a server group name that is just the application") {
        expectThat(name).isEqualTo(app)
      }
    }

    context("a server group with a stack but no detail") {
      fixture { Moniker(app = "fnord", stack = "test") }

      test("has a server group name that is the application and stack") {
        expectThat(name).isEqualTo("$app-$stack")
      }
    }

    context("a server group with both stack and detail") {
      fixture { Moniker(app = "fnord", stack = "test", detail = "foo") }

      test("has a server group name that is the application, stack, and detail") {
        expectThat(name).isEqualTo("$app-$stack-$detail")
      }
    }

    context("a server group with no stack and a detail") {
      fixture { Moniker(app = "fnord", detail = "foo") }

      test("has a server group name that is the application and detail") {
        expectThat(name).isEqualTo("$app--$detail")
      }
    }
  }
}
