package com.netflix.spinnaker.keel.jackson.mixins

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY
import com.netflix.spinnaker.keel.api.artifacts.SortingStrategy

internal interface DeliveryArtifactMixin {
  @get:JsonProperty(access = WRITE_ONLY)
  val deliveryConfigName: String

  @get:JsonIgnore
  val sortingStrategy: SortingStrategy

  @get:JsonIgnore
  val filteredByBranch : Boolean

  @get:JsonIgnore
  val filteredByPullRequest: Boolean

  @get:JsonIgnore
  val filteredByReleaseStatus: Boolean
}
