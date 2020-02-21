package com.netflix.spinnaker.keel.core.api

import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact

data class EnvironmentArtifactPin(
  val deliveryConfigName: String,
  val targetEnvironment: String,
  val reference: String,
  val type: String,
  val version: String?,
  val pinnedBy: String?,
  val comment: String?
)

data class PinnedEnvironment(
  val deliveryConfigName: String,
  val targetEnvironment: String,
  val artifact: DeliveryArtifact,
  val version: String
)

data class EnvironmentArtifactVeto(
  val deliveryConfigName: String,
  val targetEnvironment: String,
  val reference: String,
  val type: String,
  val version: String
)

data class EnvironmentArtifactVetoes(
  val deliveryConfigName: String,
  val targetEnvironment: String,
  val artifact: DeliveryArtifact,
  val versions: MutableSet<String>
)
