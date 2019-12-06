package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.api.ArtifactStatus
import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.EnvironmentArtifactsSummary
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.NoSuchArtifactException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class InMemoryArtifactRepository : ArtifactRepository {
  private val artifacts = mutableMapOf<DeliveryArtifact, MutableList<ArtifactVersionAndStatus>>()
  private val approvedVersions = mutableMapOf<Key, MutableList<String>>()
  private val deployedVersions = mutableMapOf<Key, MutableList<String>>()
  private val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }

  private data class Key(
    val artifact: DeliveryArtifact,
    val deliveryConfig: DeliveryConfig,
    val environment: String
  )

  override fun register(artifact: DeliveryArtifact) {
    try {
      val curArtifact = get(artifact.name, artifact.type)
      log.info("Artifact registration: updating {}", artifact)
      artifacts.remove(curArtifact)
    } catch (E: NoSuchArtifactException) {
      log.info("Artifact registration: creating {}", artifact)
    }
    artifacts[artifact] = mutableListOf()
  }

  override fun get(name: String, type: ArtifactType): DeliveryArtifact =
    artifacts.keys.find { it.name == name && it.type == type } ?: throw NoSuchArtifactException(name, type)

  override fun store(artifact: DeliveryArtifact, version: String, status: ArtifactStatus?): Boolean {
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

  override fun versions(name: String, type: ArtifactType, statuses: List<ArtifactStatus>): List<String> =
    versions(get(name, type), statuses)

  override fun versions(artifact: DeliveryArtifact, statuses: List<ArtifactStatus>): List<String> {
    val versions = artifacts[artifact] ?: throw NoSuchArtifactException(artifact)
    return versions
      .filter {
        if (statuses.isEmpty()) {
          // select all
          true
        } else {
          it.status in statuses
        }
      }
      .map { it.version }
      .sortedWith(artifact.versioningStrategy.comparator)
  }

  override fun approveVersionFor(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ): Boolean {
    val key = Key(artifact, deliveryConfig, targetEnvironment)
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
    val key = Key(artifact, deliveryConfig, targetEnvironment)
    val versions = approvedVersions.getOrDefault(key, mutableListOf())
    return versions.contains(version)
  }

  override fun latestVersionApprovedIn(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    targetEnvironment: String,
    statuses: List<ArtifactStatus>
  ): String? {
    val key = Key(artifact, deliveryConfig, targetEnvironment)
    val approved = approvedVersions.getOrDefault(key, mutableListOf())
    val versionsWithCorrectStatus = versions(artifact.name, artifact.type, statuses)

    // return the latest version that has been approved with the correct status
    return approved.intersect(versionsWithCorrectStatus).sortedWith(artifact.versioningStrategy.comparator).firstOrNull()
  }

  override fun wasSuccessfullyDeployedTo(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ): Boolean {
    val key = Key(artifact, deliveryConfig, targetEnvironment)
    return deployedVersions[key]?.contains(version) ?: false
  }

  override fun markAsSuccessfullyDeployedTo(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ) {
    val key = Key(artifact, deliveryConfig, targetEnvironment)
    val list = deployedVersions[key]
    if (list == null) {
      deployedVersions[key] = mutableListOf(version)
    } else {
      list.add(version)
    }
  }

  override fun versionsByEnvironment(deliveryConfig: DeliveryConfig): List<EnvironmentArtifactsSummary> {
    TODO("not implemented")
  }

  fun dropAll() {
    artifacts.clear()
    approvedVersions.clear()
    deployedVersions.clear()
  }

  private data class ArtifactVersionAndStatus(
    val version: String,
    val status: ArtifactStatus?
  )
}
