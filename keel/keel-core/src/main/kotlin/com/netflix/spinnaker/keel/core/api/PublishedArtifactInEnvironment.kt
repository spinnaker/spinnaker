package com.netflix.spinnaker.keel.core.api

import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import java.time.Instant

data class PublishedArtifactInEnvironment(
  val publishedArtifact: PublishedArtifact,
  val status: PromotionStatus,
  val environmentName: String?,
  val deployedAt: Instant? = null,
  val replacedBy: String? = null,
  val isCurrent: Boolean = false,
)
