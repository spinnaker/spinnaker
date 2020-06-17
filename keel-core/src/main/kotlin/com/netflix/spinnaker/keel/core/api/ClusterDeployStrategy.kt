package com.netflix.spinnaker.keel.core.api

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id
import com.fasterxml.jackson.annotation.JsonTypeName
import com.netflix.spinnaker.keel.constraints.AllowedTimesConstraintEvaluator
import java.time.Duration
import java.time.Duration.ZERO

@JsonTypeInfo(
  use = Id.NAME,
  include = As.PROPERTY,
  property = "strategy"
)
@JsonSubTypes(
  Type(RedBlack::class),
  Type(Highlander::class)
)
sealed class ClusterDeployStrategy {
  @get:JsonIgnore
  open val isStaggered: Boolean = false

  @get:JsonInclude(NON_EMPTY)
  open val stagger: List<StaggeredRegion> = emptyList()
  abstract fun toOrcaJobProperties(): Map<String, Any?>
  abstract fun withDefaultsOmitted(): ClusterDeployStrategy

  companion object {
    val DEFAULT_WAIT_FOR_INSTANCES_UP: Duration = Duration.ofMinutes(30)
  }
}

@JsonTypeName("red-black")
data class RedBlack(
  // defaulting to false because this rollback behavior doesn't seem to play nice with managed delivery
  val rollbackOnFailure: Boolean? = false,
  val resizePreviousToZero: Boolean? = false,
  val maxServerGroups: Int? = 2,
  val delayBeforeDisable: Duration? = ZERO,
  val delayBeforeScaleDown: Duration? = ZERO,
  val waitForInstancesUp: Duration? = DEFAULT_WAIT_FOR_INSTANCES_UP,
  // The order of this list is important for pauseTime based staggers
  @JsonInclude(NON_EMPTY)
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

@JsonTypeName("highlander")
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
      require(hours.matches(AllowedTimesConstraintEvaluator.intRange)) {
        "hours should contain a single range, i.e. 9-17 or 22-2"
      }
    }
  }

  @get:JsonIgnore
  val allowedHours: Set<Int>
    get() = AllowedTimesConstraintEvaluator.parseHours(hours)
}

private fun <T> nullIfDefault(value: T, default: T): T? =
  if (value == default) null else value
