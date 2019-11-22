package com.netflix.spinnaker.keel.api.ec2

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.*
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.*
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
sealed class ClusterDeployStrategy

@JsonTypeName("red-black")
data class RedBlack(
  val rollbackOnFailure: Boolean = true,
  val resizePreviousToZero: Boolean = false,
  val maxServerGroups: Int = 2,
  val delayBeforeDisable: Duration = ZERO,
  val delayBeforeScaleDown: Duration = ZERO
) : ClusterDeployStrategy()

@JsonTypeName("highlander")
object Highlander : ClusterDeployStrategy()
