package com.netflix.spinnaker.keel.api.ec2

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal object ClusterNameTest : JUnit5Minutests {

  fun tests() = rootContext<ClusterName> {
    context("a name with only an application") {
      fixture { ClusterName("fnord") }

      test("string value is the same as the application") {
        expectThat(toString()).isEqualTo(application)
      }
    }

    context("a name with an application and stack") {
      fixture { ClusterName("fnord", "test") }

      test("string value is the same as the application") {
        expectThat(toString()).isEqualTo("$application-$stack")
      }
    }

    context("a name with an application, stack, and detail") {
      fixture { ClusterName("fnord", "test", "temp") }

      test("string value is the same as the application") {
        expectThat(toString()).isEqualTo("$application-$stack-$detail")
      }
    }

    context("a name with an application and detail but no stack") {
      fixture { ClusterName("fnord", detail = "temp") }

      test("string value is the same as the application") {
        expectThat(toString()).isEqualTo("$application--$detail")
      }
    }
  }

}
