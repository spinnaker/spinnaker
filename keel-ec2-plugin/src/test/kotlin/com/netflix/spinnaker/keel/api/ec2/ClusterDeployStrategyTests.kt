package com.netflix.spinnaker.keel.api.ec2

import com.fasterxml.jackson.databind.node.ObjectNode
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.jackson.booleanValue
import strikt.jackson.numberValue
import strikt.jackson.path
import strikt.jackson.textValue

internal class ClusterDeployStrategyTests : JUnit5Minutests {
  object Fixture {
    val mapper = configuredObjectMapper()
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture }

    test("highlander serializes to JSON") {
      expectThat(mapper.writeValueAsString(Highlander))
        .isEqualTo("""{"strategy":"highlander"}""")
    }

    test("red-black serializes to JSON") {
      expectThat<ObjectNode>(mapper.valueToTree(RedBlack())) {
        path("strategy").textValue().isEqualTo("red-black")
        path("resizePreviousToZero").booleanValue().isFalse()
        path("rollbackOnFailure").booleanValue().isFalse()
        path("maxServerGroups").numberValue().isEqualTo(2)
        path("delayBeforeDisable").textValue().isEqualTo("PT0S")
        path("delayBeforeScaleDown").textValue().isEqualTo("PT0S")
      }
    }
  }
}
