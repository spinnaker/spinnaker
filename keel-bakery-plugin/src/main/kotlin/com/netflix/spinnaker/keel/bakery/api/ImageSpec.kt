package com.netflix.spinnaker.keel.bakery.api

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.keel.api.ArtifactStatus
import com.netflix.spinnaker.keel.api.ResourceSpec

data class ImageSpec(
  val artifactName: String,
  val baseLabel: BaseLabel,
  val baseOs: String,
  val regions: Set<String>,
  val storeType: StoreType,
  val artifactStatuses: List<ArtifactStatus> = enumValues<ArtifactStatus>().toList(),
  override val application: String // the application an image is baked in
) : ResourceSpec {
  @JsonIgnore
  override val id: String = artifactName
}
