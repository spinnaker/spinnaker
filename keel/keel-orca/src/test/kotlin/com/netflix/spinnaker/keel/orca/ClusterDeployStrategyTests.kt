package com.netflix.spinnaker.keel.orca

import com.netflix.spinnaker.keel.api.ClusterDeployStrategy.Companion.DEFAULT_WAIT_FOR_INSTANCES_UP
import com.netflix.spinnaker.keel.api.DeployHealth.NONE
import com.netflix.spinnaker.keel.api.RedBlack
import dev.minutest.junit.JUnit5Minutests
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import java.time.Duration

class ClusterDeployStrategyTests {

  @Test
  fun `can convert red black defaults to orca job properties, stage timeout is default wait + margin`() {
    val strategy = RedBlack()
    val job = strategy.toOrcaJobProperties("Amazon")

    expectThat(job).isEqualTo(
      with(strategy) {
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
      }
    )
  }

  @Test
  fun `can convert red black with overrides to orca job properties, stage timeout is default wait + margin`() {
    val strategy = RedBlack(
      delayBeforeDisable = Duration.ofMinutes(30),
      delayBeforeScaleDown = Duration.ofMinutes(30),
      waitForInstancesUp = Duration.ofMinutes(10)
    )
    val job = strategy.toOrcaJobProperties("Amazon")

    expectThat(job).isEqualTo(
      with(strategy) {
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
      }
    )
  }

  @Test
  fun `no health and amazon includes correct health`() {
    val strategy = RedBlack(health = NONE)
    val job = strategy.toOrcaJobProperties("Amazon")

    expectThat(job.get("interestingHealthProviderNames")).isA<List<String>>().containsExactly("Amazon")

  }
  @Test
  fun `no health and titus includes correct health`() {
    val strategy = RedBlack(health = NONE)
    val job = strategy.toOrcaJobProperties("Titus")

    expectThat(job.get("interestingHealthProviderNames")).isA<List<String>>().containsExactly("Titus")

  }
}
