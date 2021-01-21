package com.netflix.spinnaker.keel.orca

import com.netflix.spinnaker.keel.api.ClusterDeployStrategy
import com.netflix.spinnaker.keel.api.ClusterDeployStrategy.Companion.DEFAULT_WAIT_FOR_INSTANCES_UP
import com.netflix.spinnaker.keel.api.DeployHealth.NONE
import com.netflix.spinnaker.keel.api.RedBlack
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import java.time.Duration
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.get
import strikt.assertions.isA
import strikt.assertions.isEqualTo

class ClusterDeployStrategyTests : JUnit5Minutests {
  fun tests() = rootContext<ClusterDeployStrategy> {
    derivedContext<RedBlack>("red-black") {
      fixture { RedBlack() }

      context("conversion to orca job properties") {
        context("with defaults") {
          test("includes job properties as expected, stage timeout is default wait + margin") {
            expectThat(toOrcaJobProperties("Amazon"))
              .isEqualTo(
                mapOf(
                  "strategy" to "redblack",
                  "maxRemainingAsgs" to maxServerGroups,
                  "delayBeforeDisableSec" to delayBeforeDisable?.seconds,
                  "delayBeforeScaleDownSec" to delayBeforeScaleDown?.seconds,
                  "scaleDown" to resizePreviousToZero,
                  "rollback" to mapOf("onFailure" to rollbackOnFailure),
                  "stageTimeoutMs" to DEFAULT_WAIT_FOR_INSTANCES_UP.toMillis(),
                  "interestingHealthProviderNames" to null
                )
              )
          }
        }

        context("with overrides") {
          fixture {
            RedBlack(
              delayBeforeDisable = Duration.ofMinutes(30),
              delayBeforeScaleDown = Duration.ofMinutes(30),
              waitForInstancesUp = Duration.ofMinutes(10)
            )
          }

          test("includes job properties as expected, stage timeout is specified delays combined + margin") {
            expectThat(toOrcaJobProperties("Amazon"))
              .isEqualTo(
                mapOf(
                  "strategy" to "redblack",
                  "maxRemainingAsgs" to maxServerGroups,
                  "delayBeforeDisableSec" to delayBeforeDisable?.seconds,
                  "delayBeforeScaleDownSec" to delayBeforeScaleDown?.seconds,
                  "scaleDown" to resizePreviousToZero,
                  "rollback" to mapOf("onFailure" to rollbackOnFailure),
                  "stageTimeoutMs" to (
                    delayBeforeDisable!! +
                      delayBeforeScaleDown!! +
                      waitForInstancesUp!!
                    ).toMillis(),
                  "interestingHealthProviderNames" to null
                )
              )
          }
        }

        context("with health strategy set to NONE") {
          fixture {
            RedBlack(health = NONE)
          }

          test("includes specified instance-only health provider") {
            expectThat(toOrcaJobProperties("Amazon")) {
              get("interestingHealthProviderNames").isA<List<String>>().containsExactly("Amazon")
            }
            expectThat(toOrcaJobProperties("Titus")) {
              get("interestingHealthProviderNames").isA<List<String>>().containsExactly("Titus")
            }
          }
        }
      }
    }
  }
}
