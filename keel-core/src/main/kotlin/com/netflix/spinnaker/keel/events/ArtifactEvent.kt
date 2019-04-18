package com.netflix.spinnaker.keel.events

import com.netflix.spinnaker.kork.artifacts.model.Artifact

data class ArtifactEvent(
  val artifacts: List<Artifact>,
  val details: Map<String, Any>?
)
