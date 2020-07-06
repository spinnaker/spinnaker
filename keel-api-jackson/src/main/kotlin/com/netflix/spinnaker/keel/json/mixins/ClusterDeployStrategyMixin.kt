package com.netflix.spinnaker.keel.json.mixins

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id
import com.netflix.spinnaker.keel.api.Highlander
import com.netflix.spinnaker.keel.api.RedBlack
import com.netflix.spinnaker.keel.api.StaggeredRegion

@JsonTypeInfo(
  use = Id.NAME,
  include = As.PROPERTY,
  property = "strategy"
)
@JsonSubTypes(
  Type(value = RedBlack::class, name = "red-black"),
  Type(value = Highlander::class, name = "highlander")
)
interface ClusterDeployStrategyMixin {
  @get:JsonIgnore
  val isStaggered: Boolean

  @get:JsonInclude(NON_EMPTY)
  val stagger: List<StaggeredRegion>
}
