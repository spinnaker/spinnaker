package com.netflix.spinnaker.keel.core.api

import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact

data class PublishedArtifactInEnvironment(
  val publishedArtifact: PublishedArtifact,
  val status: PromotionStatus,
  val environmentName: String?
)
