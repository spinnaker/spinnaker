package com.netflix.spinnaker.keel.api

import java.time.Duration
import java.time.Duration.ZERO

sealed class ClusterDeployStrategy {
  open val isStaggered: Boolean = false
  open val stagger: List<StaggeredRegion> = emptyList()
  abstract fun toOrcaJobProperties(): Map<String, Any?>
  abstract fun withDefaultsOmitted(): ClusterDeployStrategy

  companion object {
    val DEFAULT_WAIT_FOR_INSTANCES_UP: Duration = Duration.ofMinutes(30)
  }
}

data class RedBlack(
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

  companion object {
    fun fromOrcaStageContext(context: Map<String, Any?>) =
      RedBlack(
        rollbackOnFailure = context["rollback"]
          ?.let {
            @Suppress("UNCHECKED_CAST")
            it as Map<String, Any>
          }
          ?.get("onFailure") as Boolean,
        resizePreviousToZero = context["scaleDown"] as Boolean,
        maxServerGroups = context["maxRemainingAsgs"].toString().toInt(),
        delayBeforeDisable = Duration.ofSeconds((context["delayBeforeDisableSec"].toString().toInt()).toLong()),
        delayBeforeScaleDown = Duration.ofSeconds((context["delayBeforeScaleDownSec"].toString().toInt()).toLong())
      )

    val DEFAULTS = RedBlack()
  }

  override fun toOrcaJobProperties() = mapOf(
    "strategy" to "redblack",
    "maxRemainingAsgs" to maxServerGroups,
    "delayBeforeDisableSec" to delayBeforeDisable?.seconds,
    "delayBeforeScaleDownSec" to delayBeforeScaleDown?.seconds,
    "scaleDown" to resizePreviousToZero,
    "rollback" to mapOf("onFailure" to rollbackOnFailure),
    "stageTimeoutMs" to (
      (waitForInstancesUp ?: DEFAULT_WAIT_FOR_INSTANCES_UP) +
        (delayBeforeDisable ?: ZERO) +
        (delayBeforeScaleDown ?: ZERO)
      ).toMillis()
  )

  override val isStaggered: Boolean
    get() = stagger.isNotEmpty()

  override fun withDefaultsOmitted() =
    RedBlack(
      maxServerGroups = nullIfDefault(maxServerGroups, DEFAULTS.maxServerGroups),
      delayBeforeDisable = nullIfDefault(delayBeforeDisable, DEFAULTS.delayBeforeDisable),
      delayBeforeScaleDown = nullIfDefault(delayBeforeScaleDown, DEFAULTS.delayBeforeScaleDown),
      resizePreviousToZero = nullIfDefault(resizePreviousToZero, DEFAULTS.resizePreviousToZero),
      rollbackOnFailure = nullIfDefault(rollbackOnFailure, DEFAULTS.rollbackOnFailure)
    )
}

object Highlander : ClusterDeployStrategy() {
  override fun toOrcaJobProperties() = mapOf(
    "strategy" to "highlander",
    "stageTimeoutMs" to DEFAULT_WAIT_FOR_INSTANCES_UP.toMillis()
  )

  override fun withDefaultsOmitted() = this
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
    private val HOUR_RANGE_PATTERN = """^\d+-\d+$""".toRegex()
  }
}

private fun <T> nullIfDefault(value: T, default: T): T? =
  if (value == default) null else value
