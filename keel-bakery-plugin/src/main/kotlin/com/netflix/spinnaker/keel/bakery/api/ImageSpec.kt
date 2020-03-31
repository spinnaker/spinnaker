package com.netflix.spinnaker.keel.bakery.api

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.UnhappyControl
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.BaseLabel
import com.netflix.spinnaker.keel.api.artifacts.StoreType
import java.time.Duration

data class ImageSpec(
  val artifactName: String,
  val baseLabel: BaseLabel,
  val baseOs: String,
  val regions: Set<String>,
  val storeType: StoreType,
  val artifactStatuses: Set<ArtifactStatus> = enumValues<ArtifactStatus>().toSet(),
  override val application: String // the application an image is baked in
) : ResourceSpec, UnhappyControl {
  @JsonIgnore
  override val id: String = artifactName

  @JsonIgnore
  override val maxDiffCount = 2

  @JsonIgnore
  override val unhappyWaitTime = Duration.ZERO
}
