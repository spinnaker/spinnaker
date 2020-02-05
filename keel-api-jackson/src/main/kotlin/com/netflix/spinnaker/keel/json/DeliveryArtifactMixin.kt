package com.netflix.spinnaker.keel.json

import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.keel.api.VersioningStrategy

internal interface DeliveryArtifactMixin {
  @get:JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  val deliveryConfigName: String

  @get:JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  val versioningStrategy: VersioningStrategy
}
