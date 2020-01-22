package com.netflix.spinnaker.keel.api

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
  abstract fun toOrcaJobProperties(): Map<String, Any?>
  @get:JsonIgnore
  open val isStaggered: Boolean = false
  @get:JsonInclude(NON_EMPTY)
  open val stagger: List<StaggeredRegion> = emptyList()
}

@JsonTypeName("red-black")
data class RedBlack(
  val rollbackOnFailure: Boolean = true,
  val resizePreviousToZero: Boolean = false,
  val maxServerGroups: Int = 2,
  val delayBeforeDisable: Duration = ZERO,
  val delayBeforeScaleDown: Duration = ZERO,
  // The order of this list is important for pauseTime based staggers
  @JsonInclude(NON_EMPTY)
  override val stagger: List<StaggeredRegion> = emptyList()
) : ClusterDeployStrategy() {
  override fun toOrcaJobProperties() = mapOf(
    "strategy" to "redblack",
    "maxRemainingAsgs" to maxServerGroups,
    "delayBeforeDisableSec" to delayBeforeDisable.seconds,
    "delayBeforeScaleDownSec" to delayBeforeScaleDown.seconds,
    "scaleDown" to resizePreviousToZero,
    "rollback" to mapOf("onFailure" to rollbackOnFailure)
  )

  override val isStaggered: Boolean
    get() = stagger.isNotEmpty()
}

@JsonTypeName("highlander")
object Highlander : ClusterDeployStrategy() {
  override fun toOrcaJobProperties() = mapOf(
    "strategy" to "highlander"
  )
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
