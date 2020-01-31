package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.api.ArtifactStatus
import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.ArtifactVersionStatus
import com.netflix.spinnaker.keel.api.ArtifactVersions
import com.netflix.spinnaker.keel.api.DebianArtifact
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.api.EnvironmentArtifactsSummary
import com.netflix.spinnaker.keel.api.PinnedEnvironment
import com.netflix.spinnaker.keel.api.PromotionStatus
import com.netflix.spinnaker.keel.api.PromotionStatus.CURRENT
import com.netflix.spinnaker.keel.api.PromotionStatus.DEPLOYING
import com.netflix.spinnaker.keel.api.PromotionStatus.PREVIOUS
import com.netflix.spinnaker.keel.persistence.ArtifactNotFoundException
import com.netflix.spinnaker.keel.persistence.ArtifactReferenceNotFoundException
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.NoSuchArtifactException
import java.util.UUID
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class InMemoryArtifactRepository : ArtifactRepository {
  // we want to store versions by name and type, not each artifact, so that we only store them once
  private val versions = mutableMapOf<VersionsKey, MutableList<ArtifactVersionAndStatus>>()

  // this contains the full artifact details
  private val artifacts = mutableMapOf<UUID, DeliveryArtifact>()

  private val approvedVersions = mutableMapOf<EnvironmentVersionsKey, MutableList<String>>()
  private val deployedVersions = mutableMapOf<EnvironmentVersionsKey, MutableList<String>>()
  private val pinnedVersions = mutableMapOf<EnvironmentVersionsKey, EnvironmentArtifactPin>()
  private val statusByEnvironment = mutableMapOf<EnvironmentVersionsKey, MutableMap<String, PromotionStatus>>()
  private val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }

  private data class VersionsKey(
    val name: String,
    val type: ArtifactType
  )

  private data class EnvironmentVersionsKey(
    val artifact: UUID,
    val deliveryConfig: DeliveryConfig,
    val environment: String
  )

  override fun register(artifact: DeliveryArtifact) {
    require(artifact.deliveryConfigName != null) { "Cannot register artifact with no delivery config name: $artifact" }

    val id = getId(artifact)
    if (id == null) {
      log.info("Artifact registration: creating {}", artifact)
      artifacts[UUID.randomUUID()] = artifact
    } else {
      log.info("Artifact registration: updating {}", artifact)
      log.info("")
      artifacts[id] = artifact
    }
    versions.putIfAbsent(VersionsKey(artifact.name, artifact.type), mutableListOf())
  }

  private fun getId(artifact: DeliveryArtifact): UUID? =
    getId(artifact.name, artifact.type, artifact.deliveryConfigName, artifact.reference)

  private fun getId(name: String, type: ArtifactType, deliveryConfigName: String?, reference: String): UUID? {
    if (deliveryConfigName == null) {
      return null
    }
    artifacts
      .forEach { (key, value) ->
        if (value.name == name && value.type == type &&
          value.deliveryConfigName == deliveryConfigName && value.reference == reference) {
          return key
        }
      }
    return null
  }

  override fun get(name: String, type: ArtifactType, deliveryConfigName: String): List<DeliveryArtifact> =
    artifacts
      .values
      .filter {
        it.name == name &&
          it.type == type &&
          it.deliveryConfigName == deliveryConfigName
      }

  override fun get(name: String, type: ArtifactType, reference: String, deliveryConfigName: String): DeliveryArtifact =
    artifacts
      .values
      .firstOrNull {
        it.name == name &&
          it.type == type &&
          it.deliveryConfigName == deliveryConfigName &&
          it.reference == reference
      } ?: throw ArtifactNotFoundException(name, type, reference, deliveryConfigName)

  override fun get(deliveryConfigName: String, reference: String, type: ArtifactType): DeliveryArtifact =
    artifacts
      .values
      .firstOrNull {
        it.deliveryConfigName == deliveryConfigName &&
          it.reference == reference &&
          it.type == type
      } ?: throw ArtifactReferenceNotFoundException(deliveryConfigName, reference, type)

  override fun store(artifact: DeliveryArtifact, version: String, status: ArtifactStatus?): Boolean =
    store(artifact.name, artifact.type, version, status)

  override fun store(name: String, type: ArtifactType, version: String, status: ArtifactStatus?): Boolean {
    if (!isRegistered(name, type)) {
      throw NoSuchArtifactException(name, type)
    }
    val versions = versions[VersionsKey(name, type)] ?: mutableListOf()
    return if (versions.none { it.version == version }) {
      versions.add(ArtifactVersionAndStatus(version, status))
      true
    } else {
      false
    }
  }

  override fun delete(artifact: DeliveryArtifact) {
    artifacts.remove(getId(artifact))
  }

  override fun isRegistered(name: String, type: ArtifactType) =
    versions.containsKey(VersionsKey(name, type))

  override fun getAll(type: ArtifactType?): List<DeliveryArtifact> =
    artifacts.values.toList().filter { type == null || it.type == type }

  override fun versions(name: String, type: ArtifactType): List<String> =
    versions.getOrDefault(VersionsKey(name, type), emptyList<ArtifactVersionAndStatus>()).map { it.version }

  override fun versions(artifact: DeliveryArtifact): List<String> {
    val versions = versions.getOrDefault(VersionsKey(artifact.name, artifact.type), null)
      ?: throw NoSuchArtifactException(artifact)
    return versions
      .filter {
        if (artifact is DebianArtifact && artifact.statuses.isNotEmpty()) {
          it.status in artifact.statuses
        } else {
          // select all
          true
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
    val artifactId = getId(artifact) ?: throw NoSuchArtifactException(artifact)
    val key = EnvironmentVersionsKey(artifactId, deliveryConfig, targetEnvironment)
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
    val artifactId = getId(artifact) ?: throw NoSuchArtifactException(artifact)
    val key = EnvironmentVersionsKey(artifactId, deliveryConfig, targetEnvironment)
    val versions = approvedVersions.getOrDefault(key, mutableListOf())
    return versions.contains(version)
  }

  override fun latestVersionApprovedIn(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    targetEnvironment: String
  ): String? {
    val artifactId = getId(artifact) ?: throw NoSuchArtifactException(artifact)
    val key = EnvironmentVersionsKey(artifactId, deliveryConfig, targetEnvironment)
    return pinnedVersions[key]?.version ?: approvedVersions
      .getOrDefault(key, mutableListOf())
      .sortedWith(artifact.versioningStrategy.comparator)
      .firstOrNull()
  }

  override fun wasSuccessfullyDeployedTo(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ): Boolean {
    val artifactId = getId(artifact) ?: throw NoSuchArtifactException(artifact)
    val key = EnvironmentVersionsKey(artifactId, deliveryConfig, targetEnvironment)
    return deployedVersions[key]?.contains(version) ?: false
  }

  override fun markAsSuccessfullyDeployedTo(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ) {
    val artifactId = getId(artifact) ?: throw NoSuchArtifactException(artifact)
    val key = EnvironmentVersionsKey(artifactId, deliveryConfig, targetEnvironment)
    val list = deployedVersions[key]
    if (list == null) {
      deployedVersions[key] = mutableListOf(version)
    } else {
      list.add(version)
    }

    val statuses = statusByEnvironment.getOrPut(key, ::mutableMapOf)
    statuses.filterValues { it == CURRENT }.forEach { statuses[it.key] = PREVIOUS }
    statuses[version] = CURRENT
  }

  override fun markAsDeployingTo(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String) {
    val artifactId = getId(artifact) ?: throw NoSuchArtifactException(artifact)
    val key = EnvironmentVersionsKey(artifactId, deliveryConfig, targetEnvironment)
    val statuses = statusByEnvironment.getOrPut(key, ::mutableMapOf)
    statuses[version] = DEPLOYING
  }

  override fun versionsByEnvironment(deliveryConfig: DeliveryConfig): List<EnvironmentArtifactsSummary> =
    deliveryConfig
      .environments
      .map { environment ->
        val artifactVersions = deliveryConfig
          .artifacts
          .map { artifact ->
            val artifactId = getId(artifact) ?: throw NoSuchArtifactException(artifact)
            val key = EnvironmentVersionsKey(artifactId, deliveryConfig, environment.name)
            val statuses = statusByEnvironment
              .getOrDefault(key, emptyMap<String, String>())
            ArtifactVersions(
              name = artifact.name,
              type = artifact.type,
              versions = ArtifactVersionStatus(
                current = statuses.filterValues { it == CURRENT }.keys.firstOrNull(),
                deploying = statuses.filterValues { it == DEPLOYING }.keys.firstOrNull(),
                pending = versions[VersionsKey(artifact.name, artifact.type)]
                  ?.filter {
                    it.status == null || it.status in ((artifact as? DebianArtifact)?.statuses
                      ?: emptySet<ArtifactStatus>())
                  }
                  ?.map { it.version }
                  ?.filter { it !in statuses.keys }
                  ?: emptyList(),
                previous = statuses.filterValues { it == PREVIOUS }.keys.toList()
              )
            )
          }
        EnvironmentArtifactsSummary(environment.name, artifactVersions)
      }

  override fun pinEnvironment(deliveryConfig: DeliveryConfig, environmentArtifactPin: EnvironmentArtifactPin) {
    val artifact = get(
      deliveryConfig.name,
      environmentArtifactPin.reference,
      ArtifactType.valueOf(environmentArtifactPin.type.toUpperCase()))

    val artifactId = getId(artifact) ?: throw NoSuchArtifactException(artifact)

    val key = EnvironmentVersionsKey(artifactId, deliveryConfig, environmentArtifactPin.targetEnvironment)
    pinnedVersions[key] = environmentArtifactPin
  }

  override fun pinnedEnvironments(deliveryConfig: DeliveryConfig): List<PinnedEnvironment> =
    pinnedVersions
      .filterKeys { it.deliveryConfig == deliveryConfig }
      .map {
        PinnedEnvironment(
          deliveryConfigName = deliveryConfig.name,
          targetEnvironment = it.value.targetEnvironment,
          artifact = get(deliveryConfig.name, it.value.reference, ArtifactType.valueOf(it.value.type.toUpperCase())),
          version = it.value.version!!)
      }

  override fun deletePin(deliveryConfig: DeliveryConfig, targetEnvironment: String) {
    pinnedVersions
      .filterKeys {
        it.deliveryConfig == deliveryConfig &&
          it.environment == targetEnvironment
      }
      .forEach { pinnedVersions.remove(it.key) }
  }

  override fun deletePin(
    deliveryConfig: DeliveryConfig,
    targetEnvironment: String,
    reference: String,
    type: ArtifactType
  ) {
    pinnedVersions.filter { (k, v) ->
      k.deliveryConfig == deliveryConfig &&
        k.environment == targetEnvironment &&
        v.reference == reference &&
        v.type == type.value()
    }
      .forEach { pinnedVersions.remove(it.key) }
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
