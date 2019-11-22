package com.netflix.spinnaker.keel.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id
import com.fasterxml.jackson.annotation.JsonTypeName
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
}

@JsonTypeName("red-black")
data class RedBlack(
  val rollbackOnFailure: Boolean = true,
  val resizePreviousToZero: Boolean = false,
  val maxServerGroups: Int = 2,
  val delayBeforeDisable: Duration = ZERO,
  val delayBeforeScaleDown: Duration = ZERO
) : ClusterDeployStrategy() {
  override fun toOrcaJobProperties() = mapOf(
    "strategy" to "redblack",
    "maxRemainingAsgs" to maxServerGroups,
    "delayBeforeDisableSec" to delayBeforeDisable.seconds,
    "delayBeforeScaleDownSec" to delayBeforeScaleDown.seconds,
    "scaleDown" to resizePreviousToZero,
    "rollback" to mapOf("onFailure" to rollbackOnFailure)
  )
}

@JsonTypeName("highlander")
object Highlander : ClusterDeployStrategy() {
  override fun toOrcaJobProperties() = mapOf(
    "strategy" to "highlander"
  )
}
