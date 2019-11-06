package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.api.ArtifactStatus
import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.NoSuchArtifactException
import com.netflix.spinnaker.keel.persistence.sortAppVersion
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class InMemoryArtifactRepository : ArtifactRepository {
  private val artifacts = mutableMapOf<DeliveryArtifact, MutableList<ArtifactVersionAndStatus>>()
  private val approvedVersions = mutableMapOf<Triple<DeliveryArtifact, DeliveryConfig, String>, MutableList<String>>()
  private val deployedVersions = mutableMapOf<Triple<DeliveryArtifact, DeliveryConfig, String>, MutableList<String>>()
  private val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }

  override fun register(artifact: DeliveryArtifact) {
    if (artifacts.containsKey(artifact)) {
      log.warn("Duplicate artifact registered: {}", artifact)
      return
    }
    artifacts[artifact] = mutableListOf()
  }

  override fun store(artifact: DeliveryArtifact, version: String, status: ArtifactStatus): Boolean {
    if (!artifacts.containsKey(artifact)) {
      throw NoSuchArtifactException(artifact)
    }
    val versions = artifacts[artifact] ?: throw IllegalArgumentException()
    return if (versions.none { it.version == version }) {
      versions.add(ArtifactVersionAndStatus(version, status))
      true
    } else {
      false
    }
  }

  override fun isRegistered(name: String, type: ArtifactType) =
    artifacts.keys.any {
      it.name == name && it.type == type
    }

  override fun getAll(type: ArtifactType?): List<DeliveryArtifact> =
    artifacts.keys.toList().filter { type == null || it.type == type }

  override fun versions(artifact: DeliveryArtifact, statuses: List<ArtifactStatus>): List<String> {
    val versions = artifacts[artifact] ?: throw NoSuchArtifactException(artifact)
    return versions.filter { it.status in statuses }.map { it.version }.sortAppVersion()
  }

  override fun approveVersionFor(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ): Boolean {
    val key = Triple(artifact, deliveryConfig, targetEnvironment)
    val versions = approvedVersions.getOrDefault(key, mutableListOf())
    val isNew = !versions.contains(version)
    versions.add(version)
    approvedVersions[key] = versions
    return isNew
  }

  override fun isApprovedFor(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ): Boolean {
    val key = Triple(artifact, deliveryConfig, targetEnvironment)
    val versions = approvedVersions.getOrDefault(key, mutableListOf())
    return versions.contains(version)
  }

  override fun latestVersionApprovedIn(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    targetEnvironment: String,
    statuses: List<ArtifactStatus>
  ): String? {
    val key = Triple(artifact, deliveryConfig, targetEnvironment)
    val approved = approvedVersions.getOrDefault(key, mutableListOf()).sortAppVersion()
    val versionsWithCorrectStatus = versions(artifact, statuses)

    // return the latest version that has been approved with the correct status
    return approved.intersect(versionsWithCorrectStatus).firstOrNull()
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
    artifacts.clear()
    approvedVersions.clear()
    deployedVersions.clear()
  }

  private data class ArtifactVersionAndStatus(
    val version: String,
    val status: ArtifactStatus
  )
}
