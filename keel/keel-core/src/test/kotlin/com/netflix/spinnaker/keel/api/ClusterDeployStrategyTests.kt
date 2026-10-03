package com.netflix.spinnaker.keel.api

import tools.jackson.databind.node.ObjectNode
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

internal class ClusterDeployStrategyTests : JUnit5Minutests {
  data class Fixture(
    val strategy: ClusterDeployStrategy
  ) {
    val mapper = configuredObjectMapper()
  }

  fun tests() = rootContext<Fixture> {
    context("highlander") {
      fixture { Fixture(Highlander()) }

      test("serializes to JSON") {
        val tree = mapper.valueToTree<ObjectNode>(strategy)
        expectThat(tree.get("strategy").textValue()).isEqualTo("highlander")
        expectThat(tree.get("health").textValue()).isEqualTo(DeployHealth.AUTO.name)
      }
    }

    context("red-black") {
      fixture { Fixture(RedBlack()) }

      test("serializes to JSON") {
        val tree = mapper.valueToTree<ObjectNode>(strategy)
        expectThat(tree.get("strategy").textValue()).isEqualTo("red-black")
        expectThat(tree.get("health").textValue()).isEqualTo(DeployHealth.AUTO.name)
        expectThat(tree.get("resizePreviousToZero").booleanValue()).isFalse()
        expectThat(tree.get("rollbackOnFailure").booleanValue()).isFalse()
        expectThat(tree.get("maxServerGroups").numberValue()).isEqualTo(2)
        expectThat(tree.get("delayBeforeDisable").textValue()).isEqualTo("PT0S")
        expectThat(tree.get("delayBeforeScaleDown").textValue()).isEqualTo("PT0S")
        expectThat(tree.path("stagger").isMissingNode).isTrue()
      }

      context("with stagger") {
        fixture {
          Fixture(
            RedBlack(
              stagger = listOf(
                StaggeredRegion(
                  region = "us-west-2",
                  hours = "12-18"
                )
              )
            )
          )
        }

        test("serializes to JSON") {
          val tree = mapper.valueToTree<ObjectNode>(strategy)
          expectThat(tree.get("strategy").textValue()).isEqualTo("red-black")
          expectThat(tree.get("resizePreviousToZero").booleanValue()).isFalse()
          expectThat(tree.get("rollbackOnFailure").booleanValue()).isFalse()
          expectThat(tree.get("maxServerGroups").numberValue()).isEqualTo(2)
          expectThat(tree.get("delayBeforeDisable").textValue()).isEqualTo("PT0S")
          expectThat(tree.get("delayBeforeScaleDown").textValue()).isEqualTo("PT0S")
          expectThat(tree.get("stagger").size()).isEqualTo(1)
          expectThat(tree.get("stagger").get(0).get("region").textValue()).isEqualTo("us-west-2")
          expectThat(tree.get("stagger").get(0).get("hours").textValue()).isEqualTo("12-18")
          expectThat(tree.path("stagger").path(0).path("allowedHours").isMissingNode).isTrue()
          expectThat(tree.path("stagger").path(0).path("pauseTime").isMissingNode).isTrue()
        }
      }
    }
  }
}
