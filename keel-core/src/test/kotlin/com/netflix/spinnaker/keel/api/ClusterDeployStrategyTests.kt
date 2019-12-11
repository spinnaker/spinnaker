package com.netflix.spinnaker.keel.api

import com.fasterxml.jackson.databind.node.ObjectNode
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue
import strikt.jackson.booleanValue
import strikt.jackson.isTextual
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
      println(mapper.writeValueAsString(RedBlack()))
      expectThat<ObjectNode>(mapper.valueToTree(RedBlack())) {
        path("strategy").textValue() isEqualTo "red-black"
        path("resizePreviousToZero").booleanValue().isFalse()
        path("rollbackOnFailure").booleanValue().isTrue()
        path("maxServerGroups").numberValue().isEqualTo(2)
        path("delayBeforeDisable").isTextual().textValue() isEqualTo "PT0S"
        path("delayBeforeScaleDown").isTextual().textValue() isEqualTo "PT0S"
      }
    }
  }
}
