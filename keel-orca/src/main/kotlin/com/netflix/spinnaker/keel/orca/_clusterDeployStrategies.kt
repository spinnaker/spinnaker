package com.netflix.spinnaker.keel.orca

import com.netflix.spinnaker.keel.api.ClusterDeployStrategy
import com.netflix.spinnaker.keel.api.ClusterDeployStrategy.Companion.DEFAULT_WAIT_FOR_INSTANCES_UP
import com.netflix.spinnaker.keel.api.Highlander
import com.netflix.spinnaker.keel.api.RedBlack
import java.time.Duration.ZERO

/**
 * Transforms [ClusterDeployStrategy] into the properties required for an Orca deploy stage.
 */
fun ClusterDeployStrategy.toOrcaJobProperties(vararg instanceOnlyHealthProviders: String): Map<String, Any?> =
  when (this) {
    is RedBlack -> mapOf(
      "strategy" to "redblack",
      "maxRemainingAsgs" to maxServerGroups,
      "delayBeforeDisableSec" to delayBeforeDisable?.seconds,
      "delayBeforeScaleDownSec" to delayBeforeScaleDown?.seconds,
      "scaleDown" to resizePreviousToZero,
      "rollback" to mapOf("onFailure" to rollbackOnFailure),
      "stageTimeoutMs" to (
        (
          waitForInstancesUp
            ?: DEFAULT_WAIT_FOR_INSTANCES_UP
          ) +
          (delayBeforeDisable ?: ZERO) +
          (delayBeforeScaleDown ?: ZERO)
        ).toMillis(),
      "interestingHealthProviderNames" to if (noHealth) {
        instanceOnlyHealthProviders.toList()
      } else {
        null
      }
    )
    is Highlander -> mapOf(
      "strategy" to "highlander",
      "stageTimeoutMs" to DEFAULT_WAIT_FOR_INSTANCES_UP.toMillis(),
      "interestingHealthProviderNames" to if (noHealth) {
        instanceOnlyHealthProviders.toList()
      } else {
        null
      }
    )
  }
