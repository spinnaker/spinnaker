package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintStateAttributes

data class ManualJudgementConstraintAttributes(
  val slackDetails: List<OriginalSlackMessageDetail> = emptyList()
) : ConstraintStateAttributes("manual-judgement")

data class OriginalSlackMessageDetail(
  val timestamp: String,
  val channel: String,
  val artifactCandidate: PublishedArtifact,
  val currentArtifact: PublishedArtifact? = null,
  val pinnedArtifact: PublishedArtifact? = null,
  val deliveryArtifact: DeliveryArtifact,
  val targetEnvironment: String
)
