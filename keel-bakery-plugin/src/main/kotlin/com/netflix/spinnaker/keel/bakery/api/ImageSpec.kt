package com.netflix.spinnaker.keel.bakery.api

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.keel.api.ResourceSpec

data class ImageSpec(
  val artifactName: String,
  val baseLabel: BaseLabel,
  val baseOs: String,
  val regions: Set<String>,
  val storeType: StoreType,
  override val application: String // the application an image is baked in
) : ResourceSpec {
  @JsonIgnore
  override val name: String = artifactName
}
