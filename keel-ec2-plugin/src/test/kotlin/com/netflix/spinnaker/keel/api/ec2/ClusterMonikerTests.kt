package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.ec2.cluster.Moniker
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal class ClusterMonikerTests : JUnit5Minutests {
  fun tests() = rootContext<Moniker> {
    context("a cluster with no stack or detail") {
      fixture { Moniker("fnord") }

      test("has a cluster name that is just the application") {
        expectThat(cluster).isEqualTo(application)
      }
    }

    context("a cluster with a stack but no detail") {
      fixture { Moniker("fnord", "test") }

      test("has a cluster name that is the application and stack") {
        expectThat(cluster).isEqualTo("$application-$stack")
      }
    }

    context("a cluster with both stack and detail") {
      fixture { Moniker("fnord", "test", "foo") }

      test("has a cluster name that is the application, stack, and detail") {
        expectThat(cluster).isEqualTo("$application-$stack-$detail")
      }
    }

    context("a cluster with no stack and a detail") {
      fixture { Moniker("fnord", detail = "foo") }

      test("has a cluster name that is the application and detail") {
        expectThat(cluster).isEqualTo("$application--$detail")
      }
    }
  }
}
