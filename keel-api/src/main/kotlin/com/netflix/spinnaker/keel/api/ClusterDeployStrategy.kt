package com.netflix.spinnaker.keel.api

import java.time.Duration
import java.time.Duration.ZERO

sealed class ClusterDeployStrategy {
  open val isStaggered: Boolean = false
  open val stagger: List<StaggeredRegion> = emptyList()
  abstract val noHealth: Boolean

  companion object {
    val DEFAULT_WAIT_FOR_INSTANCES_UP: Duration = Duration.ofMinutes(30)
  }
}

data class RedBlack(
  override val noHealth: Boolean = false,
  // defaulting to false because this rollback behavior doesn't seem to play nice with managed delivery
  val rollbackOnFailure: Boolean? = false,
  val resizePreviousToZero: Boolean? = false,
  val maxServerGroups: Int? = 2,
  val delayBeforeDisable: Duration? = ZERO,
  val delayBeforeScaleDown: Duration? = ZERO,
  val waitForInstancesUp: Duration? = DEFAULT_WAIT_FOR_INSTANCES_UP,
  // The order of this list is important for pauseTime based staggers
  override val stagger: List<StaggeredRegion> = emptyList()
) : ClusterDeployStrategy() {
  override val isStaggered: Boolean
    get() = stagger.isNotEmpty()
}

data class Highlander(
  override val noHealth: Boolean = false
) : ClusterDeployStrategy()

fun ClusterDeployStrategy.withDefaultsOmitted(): ClusterDeployStrategy =
  when (this) {
    is RedBlack -> {
      val defaults = RedBlack()
      RedBlack(
        maxServerGroups = nullIfDefault(maxServerGroups, defaults.maxServerGroups),
        delayBeforeDisable = nullIfDefault(delayBeforeDisable, defaults.delayBeforeDisable),
        delayBeforeScaleDown = nullIfDefault(delayBeforeScaleDown, defaults.delayBeforeScaleDown),
        resizePreviousToZero = nullIfDefault(resizePreviousToZero, defaults.resizePreviousToZero),
        rollbackOnFailure = nullIfDefault(rollbackOnFailure, defaults.rollbackOnFailure)
      )
    }
    else -> this
  }

/**
 * Allows the deployment of multi-region clusters to be staggered by region.
 *
 * @param region: The region to stagger
 * @param hours: If set, this region will only be deployed to during these hours.
 *  Should be a single range (i.e. 9-17) The timezone will be whatever is used in
 *  orca for for RestrictedExcutionWindows (defaults in Orca to America/Los_Angeles)
 * @param pauseTime: If set, pause for the given duration AFTER the deployment
 *  of this region completes
 *
 * Any regions omitted are expected to be deployed in parallel after the final staggered
 * region (and its optional [pauseTime]) have completed.
 *
 */
data class StaggeredRegion(
  val region: String,
  val hours: String? = null,
  val pauseTime: Duration? = null
) {
  init {
    require(hours != null || pauseTime != null) {
      "one of allowedHours or pauseTime must be set"
    }

    if (hours != null) {
      require(hours.matches(HOUR_RANGE_PATTERN)) {
        "hours should contain a single range, i.e. 9-17 or 22-2"
      }
    }
  }

  companion object {
    private val HOUR_RANGE_PATTERN =
      """^\d+-\d+$""".toRegex()
  }
}

private fun <T> nullIfDefault(value: T, default: T): T? =
  if (value == default) null else value
