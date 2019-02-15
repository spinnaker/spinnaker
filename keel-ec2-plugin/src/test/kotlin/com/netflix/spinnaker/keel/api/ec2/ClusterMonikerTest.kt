package com.netflix.spinnaker.keel.api.ec2

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal class ClusterMonikerTest : JUnit5Minutests {
  fun tests() = rootContext<ClusterMoniker> {
    context("a cluster with no stack or detail") {
      fixture { ClusterMoniker("fnord") }

      test("has a cluster name that is just the application") {
        expectThat(cluster).isEqualTo(application)
      }
    }

    context("a cluster with a stack but no detail") {
      fixture { ClusterMoniker("fnord", "test") }

      test("has a cluster name that is the application and stack") {
        expectThat(cluster).isEqualTo("$application-$stack")
      }
    }

    context("a cluster with both stack and detail") {
      fixture { ClusterMoniker("fnord", "test", "foo") }

      test("has a cluster name that is the application, stack, and detail") {
        expectThat(cluster).isEqualTo("$application-$stack-$detail")
      }
    }

    context("a cluster with no stack and a detail") {
      fixture { ClusterMoniker("fnord", detail = "foo") }

      test("has a cluster name that is the application and detail") {
        expectThat(cluster).isEqualTo("$application--$detail")
      }
    }
  }
}
