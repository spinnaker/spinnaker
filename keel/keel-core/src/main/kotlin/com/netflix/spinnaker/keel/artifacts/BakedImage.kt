package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.artifacts.BaseLabel
import java.time.Instant

data class BakedImage(
  val name: String,
  val baseLabel: BaseLabel,
  val baseOs: String,
  val vmType: String,
  val cloudProvider: String,
  val appVersion: String,
  val baseAmiName: String,
  val amiIdsByRegion: Map<String, String>,
  val timestamp: Instant
) {
  fun presentInAllRegions(regions: Set<String>) =
    amiIdsByRegion.keys.containsAll(regions)
}
