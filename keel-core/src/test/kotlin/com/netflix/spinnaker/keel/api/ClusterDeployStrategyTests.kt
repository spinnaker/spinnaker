package com.netflix.spinnaker.keel.api

import com.fasterxml.jackson.databind.node.ObjectNode
import com.netflix.spinnaker.keel.core.api.ClusterDeployStrategy
import com.netflix.spinnaker.keel.core.api.ClusterDeployStrategy.Companion.DEFAULT_WAIT_FOR_INSTANCES_UP
import com.netflix.spinnaker.keel.core.api.Highlander
import com.netflix.spinnaker.keel.core.api.RedBlack
import com.netflix.spinnaker.keel.core.api.StaggeredRegion
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import java.time.Duration
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue
import strikt.jackson.at
import strikt.jackson.booleanValue
import strikt.jackson.hasSize
import strikt.jackson.isArray
import strikt.jackson.isMissing
import strikt.jackson.isTextual
import strikt.jackson.numberValue
import strikt.jackson.path
import strikt.jackson.textValue

internal class ClusterDeployStrategyTests : JUnit5Minutests {
  data class Fixture(
    val strategy: ClusterDeployStrategy
  ) {
    val mapper = configuredObjectMapper()
  }

  fun tests() = rootContext<Fixture> {
    context("highlander") {
      fixture { Fixture(Highlander) }

      test("serializes to JSON") {
        expectThat(mapper.writeValueAsString(strategy))
          .isEqualTo("""{"strategy":"highlander"}""")
      }
    }

    context("red-black") {
      fixture { Fixture(RedBlack()) }

      test("serializes to JSON") {
        println(mapper.writeValueAsString(strategy))
        expectThat<ObjectNode>(mapper.valueToTree(strategy)) {
          path("strategy").textValue() isEqualTo "red-black"
          path("resizePreviousToZero").booleanValue().isFalse()
          path("rollbackOnFailure").booleanValue().isTrue()
          path("maxServerGroups").numberValue().isEqualTo(2)
          path("delayBeforeDisable").isTextual().textValue() isEqualTo "PT0S"
          path("delayBeforeScaleDown").isTextual().textValue() isEqualTo "PT0S"
          path("stagger").isMissing()
        }
      }

      context("with stagger") {
        fixture {
          Fixture(
            RedBlack(
              stagger = listOf(
                StaggeredRegion(
                  region = "us-west-2",
                  hours = "12-18")
              )
            )
          )
        }

        test("serializes to JSON") {
          println(mapper.writeValueAsString(strategy))
          expectThat<ObjectNode>(mapper.valueToTree(strategy)) {
            path("strategy").textValue() isEqualTo "red-black"
            path("resizePreviousToZero").booleanValue().isFalse()
            path("rollbackOnFailure").booleanValue().isTrue()
            path("maxServerGroups").numberValue().isEqualTo(2)
            path("delayBeforeDisable").isTextual().textValue() isEqualTo "PT0S"
            path("delayBeforeScaleDown").isTextual().textValue() isEqualTo "PT0S"
            path("stagger").isArray().hasSize(1)
            at("/stagger/0/region").isTextual().textValue() isEqualTo "us-west-2"
            at("/stagger/0/hours").isTextual().textValue() isEqualTo "12-18"
            at("/stagger/0/allowedHours").isMissing()
            at("/stagger/0/pauseTime").isMissing()
          }
        }
      }

      context("conversion to orca job properties") {
        context("with defaults") {
          test("includes job properties as expected, stage timeout is default wait + margin") {
            expectThat((strategy as RedBlack).toOrcaJobProperties()).isEqualTo(
              mapOf(
                "strategy" to "redblack",
                "maxRemainingAsgs" to strategy.maxServerGroups,
                "delayBeforeDisableSec" to strategy.delayBeforeDisable?.seconds,
                "delayBeforeScaleDownSec" to strategy.delayBeforeScaleDown?.seconds,
                "scaleDown" to strategy.resizePreviousToZero,
                "rollback" to mapOf("onFailure" to strategy.rollbackOnFailure),
                "stageTimeoutMs" to DEFAULT_WAIT_FOR_INSTANCES_UP.toMillis()
              )
            )
          }
        }

        context("with overrides") {
          fixture {
            Fixture(
              RedBlack(
                delayBeforeDisable = Duration.ofMinutes(30),
                delayBeforeScaleDown = Duration.ofMinutes(30),
                waitForInstancesUp = Duration.ofMinutes(10)
              )
            )
          }

          test("includes job properties as expected, stage timeout is specified delays combined + margin") {
            expectThat((strategy as RedBlack).toOrcaJobProperties()).isEqualTo(
              mapOf(
                "strategy" to "redblack",
                "maxRemainingAsgs" to strategy.maxServerGroups,
                "delayBeforeDisableSec" to strategy.delayBeforeDisable?.seconds,
                "delayBeforeScaleDownSec" to strategy.delayBeforeScaleDown?.seconds,
                "scaleDown" to strategy.resizePreviousToZero,
                "rollback" to mapOf("onFailure" to strategy.rollbackOnFailure),
                "stageTimeoutMs" to (
                  strategy.delayBeforeDisable!! +
                  strategy.delayBeforeScaleDown!! +
                  strategy.waitForInstancesUp!!
                ).toMillis()
              )
            )
          }
        }
      }
    }
  }
}
