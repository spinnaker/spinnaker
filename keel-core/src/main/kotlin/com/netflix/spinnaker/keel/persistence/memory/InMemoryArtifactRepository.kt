package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.core.TagComparator
import com.netflix.spinnaker.keel.core.api.ArtifactSummaryInEnvironment
import com.netflix.spinnaker.keel.core.api.ArtifactVersionStatus
import com.netflix.spinnaker.keel.core.api.ArtifactVersions
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVetoes
import com.netflix.spinnaker.keel.core.api.EnvironmentSummary
import com.netflix.spinnaker.keel.core.api.Pinned
import com.netflix.spinnaker.keel.core.api.PinnedEnvironment
import com.netflix.spinnaker.keel.core.api.PromotionStatus
import com.netflix.spinnaker.keel.core.api.PromotionStatus.APPROVED
import com.netflix.spinnaker.keel.core.api.PromotionStatus.CURRENT
import com.netflix.spinnaker.keel.core.api.PromotionStatus.DEPLOYING
import com.netflix.spinnaker.keel.core.api.PromotionStatus.PREVIOUS
import com.netflix.spinnaker.keel.core.api.PromotionStatus.SKIPPED
import com.netflix.spinnaker.keel.core.api.PromotionStatus.VETOED
import com.netflix.spinnaker.keel.core.comparator
import com.netflix.spinnaker.keel.exceptions.InvalidRegexException
import com.netflix.spinnaker.keel.persistence.ArtifactNotFoundException
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.NoSuchArtifactException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.Instant.EPOCH
import java.util.UUID
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class InMemoryArtifactRepository(
  private val clock: Clock = Clock.systemUTC()
) : ArtifactRepository {
  // we want to store versions by name and type, not each artifact, so that we only store them once
  private val versions = mutableMapOf<VersionsKey, MutableList<ArtifactVersionAndStatus>>()

  // this contains the full artifact details
  private val artifacts = mutableMapOf<UUID, DeliveryArtifact>()

  private val approvedVersions = mutableMapOf<EnvironmentVersionsKey, MutableList<String>>()
  private val deployedVersions = mutableMapOf<EnvironmentVersionsKey, MutableList<Pair<String, Instant>>>()
  private val vetoedVersions = mutableMapOf<EnvironmentVersionsKey, MutableList<String>>()
  private val pinnedVersions = mutableMapOf<EnvironmentVersionsKey, PinSummary>()
  private val skippedVersions = mutableMapOf<EnvironmentVersionsKey, MutableList<Skipped>>()
  private val statusByEnvironment = mutableMapOf<EnvironmentVersionsKey, MutableMap<String, PromotionStatus>>()
  private val vetoReference = mutableMapOf<EnvironmentVersionsKey, MutableMap<String, String>>()
  private val lastCheckTimes = mutableMapOf<DeliveryArtifact, Instant>()
  private val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }

  fun dropAll() {
    artifacts.clear()
    approvedVersions.clear()
    deployedVersions.clear()
    versions.clear()
    vetoedVersions.clear()
    pinnedVersions.clear()
    statusByEnvironment.clear()
    vetoReference.clear()
    lastCheckTimes.clear()
  }

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
    lastCheckTimes[artifact] = EPOCH
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
      } ?: throw ArtifactNotFoundException(reference, deliveryConfigName)

  override fun get(deliveryConfigName: String, reference: String): DeliveryArtifact =
    artifacts
      .values
      .firstOrNull {
        it.deliveryConfigName == deliveryConfigName && it.reference == reference
      } ?: throw ArtifactNotFoundException(reference, deliveryConfigName)

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
        } else if (artifact is DockerArtifact) {
          // we only want to give valid versions, so this will filter out tags like "latest"
          // and others that don't fit the chosen versioning strategy.
          try {
            it.version != "latest" && TagComparator.parseWithRegex(it.version, artifact.tagVersionStrategy, artifact.captureGroupRegex) != null
          } catch (e: InvalidRegexException) {
            false
          }
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
    val statuses = statusByEnvironment.getOrPut(key, ::mutableMapOf)
    statuses[version] = APPROVED
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
    return deployedVersions[key]?.any { (v, _) -> v == version } ?: false
  }

  override fun isCurrentlyDeployedTo(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ): Boolean {
    val artifactId = getId(artifact) ?: throw NoSuchArtifactException(artifact)
    val key = EnvironmentVersionsKey(artifactId, deliveryConfig, targetEnvironment)

    val statuses = statusByEnvironment.getOrPut(key, ::mutableMapOf)
    return statuses.filterKeys { it == version }.filterValues { it == CURRENT }.isNotEmpty()
  }

  override fun markAsSuccessfullyDeployedTo(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ) {
    val artifactId = getId(artifact) ?: throw NoSuchArtifactException(artifact)
    val key = EnvironmentVersionsKey(artifactId, deliveryConfig, targetEnvironment)
    val deployedVersion = Pair(version, clock.instant())
    val list = deployedVersions[key]
    if (list == null) {
      deployedVersions[key] = mutableListOf(deployedVersion)
    } else {
      list.add(deployedVersion)
    }

    val statuses = statusByEnvironment.getOrPut(key, ::mutableMapOf)
    // update all previous "current" versions to "previous"
    statuses.filterValues { it == CURRENT }.forEach { statuses[it.key] = PREVIOUS }
    // update all previous "approved" versions with a lower version number to "skipped"
    statuses
      .filterValues { it == APPROVED }
      .filterKeys { artifact.versioningStrategy.comparator.compare(it, version) > 0 }
      .forEach {
        statuses[it.key] = SKIPPED
      }
    statuses[version] = CURRENT
  }

  override fun vetoedEnvironmentVersions(deliveryConfig: DeliveryConfig): List<EnvironmentArtifactVetoes> {
    val vetoes: MutableMap<EnvironmentVersionsKey, EnvironmentArtifactVetoes> = mutableMapOf()

    deliveryConfig.environments.forEach { environment ->
      deliveryConfig.artifacts.forEach { artifact ->
        getId(artifact)
          ?.let { artifactId ->
            val key = EnvironmentVersionsKey(artifactId, deliveryConfig, environment.name)
            statusByEnvironment[key]
              ?.filter { it.value == VETOED }
              ?.map {
                val version = it.key
                vetoes.getOrPut(key, {
                  EnvironmentArtifactVetoes(
                    deliveryConfigName = deliveryConfig.name,
                    targetEnvironment = environment.name,
                    artifact = artifact,
                    versions = mutableSetOf()
                  )
                })
                  .versions.add(version)
              }
          }
      }
    }

    return vetoes.values.toList()
  }

  override fun markAsVetoedIn(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String,
    force: Boolean
  ): Boolean {
    val artifactId = getId(artifact) ?: throw NoSuchArtifactException(artifact)
    val key = EnvironmentVersionsKey(artifactId, deliveryConfig, targetEnvironment)
    pinnedVersions[key]?.let {
      if (it.version == version) {
        log.warn(
          "Pinned artifact version cannot be vetoed: " +
            "deliveryConfig=${deliveryConfig.name}, " +
            "environment=$targetEnvironment, " +
            "artifactVersion=$version")
        return false
      }
    }

    val ref = vetoReference.getOrPut(key, ::mutableMapOf)
    if (ref.containsKey(version) && !force) {
      log.warn(
        "Not vetoing artifact version as it appears to have already been an automated rollback target: " +
          "deliveryConfig=${deliveryConfig.name}, " +
          "environment=$targetEnvironment, " +
          "artifactVersion=$version, " +
          "priorVersionReference=${ref[version]}")
      return false
    }

    val prior = approvedVersions
      .getOrDefault(key, mutableListOf())
      .asSequence()
      .filter { it != version }
      .sortedWith(artifact.versioningStrategy.comparator)
      .firstOrNull()

    if (prior != null) {
      ref[version] = prior
      ref[prior] = version
    } else {
      ref[version] = version
    }

    approvedVersions.getOrPut(key, ::mutableListOf).remove(version)
    deployedVersions.getOrPut(key, ::mutableListOf).removeIf { (v, _) -> v == version }
    vetoedVersions.getOrPut(key, ::mutableListOf).add(version)

    val statuses = statusByEnvironment.getOrPut(key, ::mutableMapOf)
    statuses[version] = VETOED
    return true
  }

  override fun deleteVeto(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ) {
    val artifactId = getId(artifact) ?: throw NoSuchArtifactException(artifact)
    val key = EnvironmentVersionsKey(artifactId, deliveryConfig, targetEnvironment)
    vetoedVersions.getOrPut(key, ::mutableListOf).remove(version)
    val statuses = statusByEnvironment.getOrPut(key, ::mutableMapOf)
    statuses[version] = APPROVED
    approvedVersions.getOrPut(key, ::mutableListOf).add(version)
    val ref = vetoReference.getOrPut(key, ::mutableMapOf)
    val prior = ref[version]
    if (prior != version && ref[prior] == version) {
      ref.remove(prior)
    }
    ref.remove(version)
  }

  override fun markAsDeployingTo(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String) {
    val artifactId = getId(artifact) ?: throw NoSuchArtifactException(artifact)
    val key = EnvironmentVersionsKey(artifactId, deliveryConfig, targetEnvironment)
    val statuses = statusByEnvironment.getOrPut(key, ::mutableMapOf)
    statuses[version] = DEPLOYING
  }

  override fun markAsSkipped(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String, supersededByVersion: String) {
    val artifactId = getId(artifact) ?: throw NoSuchArtifactException(artifact)
    val key = EnvironmentVersionsKey(artifactId, deliveryConfig, targetEnvironment)
    val statuses = statusByEnvironment.getOrPut(key, ::mutableMapOf)
    statuses[version] = SKIPPED
    skippedVersions.getOrPut(key, ::mutableListOf).add(Skipped(version, supersededByVersion, clock.instant()))
  }

  override fun getEnvironmentSummaries(deliveryConfig: DeliveryConfig): List<EnvironmentSummary> {
    val pinnedEnvs = getPinnedEnvironments(deliveryConfig)

    return deliveryConfig
      .environments
      .map { environment ->
        val artifactVersions = deliveryConfig
          .artifacts
          .map { artifact ->
            val artifactId = getId(artifact) ?: throw NoSuchArtifactException(artifact)
            val key = EnvironmentVersionsKey(artifactId, deliveryConfig, environment.name)
            val statuses = statusByEnvironment
              .getOrDefault(key, emptyMap<String, String>())

            val currentVersion = statuses.filterValues { it == CURRENT }.keys.firstOrNull()
            val pending = versions[VersionsKey(artifact.name, artifact.type)]
              ?.filter {
                when (artifact) {
                  is DebianArtifact -> {
                    artifact.statuses.isEmpty() || it.status in artifact.statuses
                  }
                  is DockerArtifact -> {
                    it.version != "latest" && TagComparator.parseWithRegex(it.version, artifact.tagVersionStrategy, artifact.captureGroupRegex) != null
                  }
                }
              }
              ?.map { it.version }
              ?.filter { it !in statuses.keys }
              ?: emptyList()

            ArtifactVersions(
              name = artifact.name,
              type = artifact.type,
              reference = artifact.reference,
              statuses = when (artifact) {
                is DebianArtifact -> artifact.statuses
                else -> emptySet()
              },
              versions = ArtifactVersionStatus(
                current = currentVersion,
                deploying = statuses.filterValues { it == DEPLOYING }.keys.firstOrNull(),
                pending = removeOlderIfCurrentExists(artifact, currentVersion, pending),
                approved = statuses.filterValues { it == APPROVED }.keys.toList(),
                previous = statuses.filterValues { it == PREVIOUS }.keys.toList(),
                vetoed = statuses.filterValues { it == VETOED }.keys.toList(),
                skipped = removeNewerIfCurrentExists(artifact, currentVersion, pending).plus(statuses.filterValues { it == SKIPPED }.keys.toList())
              ),
              pinnedVersion = pinnedEnvs.find { it.targetEnvironment == environment.name }?.version
            )
          }
          .toSet()
        EnvironmentSummary(environment, artifactVersions)
      }
  }

  private fun EnvironmentArtifactPin.toSummary() =
    PinSummary(
      reference = reference,
      targetEnvironment = targetEnvironment,
      version = version,
      pinnedAt = clock.instant(),
      pinnedBy = pinnedBy,
      comment = comment
    )

  override fun pinEnvironment(deliveryConfig: DeliveryConfig, environmentArtifactPin: EnvironmentArtifactPin) {
    val artifact = get(
      deliveryConfig.name,
      environmentArtifactPin.reference)

    val artifactId = getId(artifact) ?: throw NoSuchArtifactException(artifact)

    val key = EnvironmentVersionsKey(artifactId, deliveryConfig, environmentArtifactPin.targetEnvironment)
    pinnedVersions[key] = environmentArtifactPin.toSummary()
  }

  override fun getPinnedEnvironments(deliveryConfig: DeliveryConfig): List<PinnedEnvironment> =
    pinnedVersions
      .filterKeys { it.deliveryConfig == deliveryConfig }
      .map {
        PinnedEnvironment(
          deliveryConfigName = deliveryConfig.name,
          targetEnvironment = it.value.targetEnvironment,
          artifact = get(deliveryConfig.name, it.value.reference),
          version = it.value.version!!,
          pinnedBy = it.value.pinnedBy,
          pinnedAt = it.value.pinnedAt,
          comment = it.value.comment
        )
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
    reference: String
  ) {
    pinnedVersions.filter { (k, v) ->
      k.deliveryConfig == deliveryConfig &&
        k.environment == targetEnvironment &&
        v.reference == reference
    }
      .forEach { pinnedVersions.remove(it.key) }
  }

  override fun getArtifactSummaryInEnvironment(
    deliveryConfig: DeliveryConfig,
    environmentName: String,
    artifactReference: String,
    version: String
  ): ArtifactSummaryInEnvironment? {
    val artifact = deliveryConfig.artifacts.find { it ->
      it.deliveryConfigName == deliveryConfig.name && it.reference == artifactReference
    } ?: throw ArtifactNotFoundException(artifactReference, deliveryConfig.name)

    val artifactId = getId(artifact) ?: throw NoSuchArtifactException(artifact)
    val key = EnvironmentVersionsKey(artifactId, deliveryConfig, environmentName)
    val statuses = statusByEnvironment.getOrDefault(key, emptyMap<String, String>())

    val artifactDeployedVersions = deployedVersions[key]
      ?.sortedBy { (_, deployedAt) -> deployedAt } // ascending because we want to get the replacement deployment using `firstOrNull` below
    val deployedAt = artifactDeployedVersions?.find { (ver, _) -> ver == version }?.second
    var (replacedBy, replacedAt) = artifactDeployedVersions?.firstOrNull { (ver, at) ->
      ver != version && deployedAt != null && at.isAfter(deployedAt)
    }
      ?: Pair(null, null)

    // get replaced by info if the version was skipped
    if (replacedAt == null && replacedBy == null) {
      skippedVersions.getOrDefault(key, emptyList<Skipped>())
        .find { it.version == version }
        ?.let { skipped ->
          replacedAt = skipped.replacedAt
          replacedBy = skipped.replacedByVersion
        }
    }
    val pinned: Pinned? = pinnedVersions[key]?.let { it ->
      Pinned(at = it.pinnedAt, by = it.pinnedBy, comment = it.comment)
    }

    return ArtifactSummaryInEnvironment(
      environment = environmentName,
      version = version,
      state = statuses.filterKeys { it == version }.values.firstOrNull()
        ?.toString()?.toLowerCase()
        ?: PromotionStatus.PENDING.name.toLowerCase(),
      deployedAt = deployedAt,
      replacedAt = replacedAt,
      replacedBy = replacedBy,
      isPinned = pinned != null,
      pinned = pinned
    )
  }

  override fun itemsDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<DeliveryArtifact> {
    val cutoff = clock.instant().minus(minTimeSinceLastCheck)
    return lastCheckTimes
      .filter { it.value <= cutoff }
      .keys
      .take(limit)
      .also { artifacts ->
        artifacts.forEach {
          lastCheckTimes[it] = clock.instant()
        }
      }
      .toList()
  }

  private data class Skipped(
    val version: String,
    val replacedByVersion: String,
    val replacedAt: Instant
  )

  private data class ArtifactVersionAndStatus(
    val version: String,
    val status: ArtifactStatus?
  )

  private data class PinSummary(
    val targetEnvironment: String,
    val reference: String,
    val version: String?,
    val pinnedBy: String?,
    val pinnedAt: Instant,
    val comment: String?
  )
}
