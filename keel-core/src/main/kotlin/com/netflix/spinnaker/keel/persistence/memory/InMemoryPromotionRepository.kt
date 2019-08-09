package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.persistence.PromotionRepository

class InMemoryPromotionRepository : PromotionRepository {

  private val approvedVersions = mutableMapOf<Triple<DeliveryArtifact, DeliveryConfig, String>, String>()
  private val deployedVersions = mutableMapOf<Triple<DeliveryArtifact, DeliveryConfig, String>, MutableList<String>>()

  override fun approveVersionFor(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ) {
    val key = Triple(artifact, deliveryConfig, targetEnvironment)
    approvedVersions[key] = version
  }

  override fun latestVersionApprovedIn(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    targetEnvironment: String
  ): String? {
    val key = Triple(artifact, deliveryConfig, targetEnvironment)
    return approvedVersions[key]
  }

  override fun wasSuccessfullyDeployedTo(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ): Boolean {
    val key = Triple(artifact, deliveryConfig, targetEnvironment)
    return deployedVersions[key]?.contains(version) ?: false
  }

  override fun markAsSuccessfullyDeployedTo(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ) {
    val key = Triple(artifact, deliveryConfig, targetEnvironment)
    val list = deployedVersions[key]
    if (list == null) {
      deployedVersions[key] = mutableListOf(version)
    } else {
      list.add(version)
    }
  }

  fun dropAll() {
    approvedVersions.clear()
  }
}
