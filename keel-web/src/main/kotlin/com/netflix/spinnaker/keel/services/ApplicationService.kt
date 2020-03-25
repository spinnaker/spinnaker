package com.netflix.spinnaker.keel.services

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType.deb
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType.docker
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.core.api.ArtifactSummary
import com.netflix.spinnaker.keel.core.api.ArtifactSummaryInEnvironment
import com.netflix.spinnaker.keel.core.api.ArtifactVersionSummary
import com.netflix.spinnaker.keel.core.api.ArtifactVersions
import com.netflix.spinnaker.keel.core.api.BuildMetadata
import com.netflix.spinnaker.keel.core.api.EnvironmentSummary
import com.netflix.spinnaker.keel.core.api.GitMetadata
import com.netflix.spinnaker.keel.core.api.PromotionStatus
import com.netflix.spinnaker.keel.core.api.ResourceSummary
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Service object that offers high-level APIs for application-related operations.
 */
@Component
class ApplicationService(
  private val repository: KeelRepository,
  private val artifactRepository: ArtifactRepository
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  fun hasManagedResources(application: String) = repository.hasManagedResources(application)

  fun getConstraintStatesFor(application: String) = repository.constraintStateFor(application)

  /**
   * Returns a list of [ResourceSummary] for the specified application.
   *
   * This function assumes there's a single delivery config associated with the application.
   */
  fun getResourceSummariesFor(application: String): List<ResourceSummary> =
    getFirstDeliveryConfigFor(application)
      ?.let { deliveryConfig ->
        repository.getResourceSummaries(deliveryConfig)
      }
      ?: emptyList()

  /**
   * Returns a list of [EnvironmentSummary] for the specific application.
   *
   * This function assumes there's a single delivery config associated with the application.
   */
  fun getEnvironmentSummariesFor(application: String): List<EnvironmentSummary> =
    getFirstDeliveryConfigFor(application)
      ?.let { deliveryConfig ->
        artifactRepository.getEnvironmentSummaries(deliveryConfig)
      }
      ?: emptyList()

  /**
   * Returns a list of [ArtifactSummary] for the specified application by traversing the list of [EnvironmentSummary]
   * for the application and reindexing the data so that it matches the right format.
   *
   * This function assumes there's a single delivery config associated with the application.
   */
  fun getArtifactSummariesFor(application: String): List<ArtifactSummary> {
    val deliveryConfig = getFirstDeliveryConfigFor(application)
      ?: throw InvalidRequestException("No delivery config found for application $application")

    val environmentSummaries = getEnvironmentSummariesFor(application)

    return deliveryConfig.artifacts.map { artifact ->
      artifactRepository.versions(artifact).map { version ->
        val artifactSummariesInEnvironments = mutableSetOf<ArtifactSummaryInEnvironment>()

        environmentSummaries.forEach { environmentSummary ->
          environmentSummary.getArtifactPromotionStatus(artifact, version)?.let { status ->
            if (status == PromotionStatus.PENDING) {
              ArtifactSummaryInEnvironment(
                environment = environmentSummary.name,
                version = version,
                state = status.name.toLowerCase()
              )
            } else {
              artifactRepository.getArtifactSummaryInEnvironment(
                deliveryConfig = deliveryConfig,
                environmentName = environmentSummary.name,
                artifactName = artifact.name,
                artifactType = artifact.type,
                version = version
              )
            }?.also { artifactSummaryInEnvironment ->
              artifactSummariesInEnvironments.add(artifactSummaryInEnvironment)
            }
          }
        }

        return@map versionToSummary(artifact, version, artifactSummariesInEnvironments.toSet())
      }.let { artifactVersionSummaries ->
        ArtifactSummary(
          name = artifact.name,
          type = artifact.type,
          versions = artifactVersionSummaries.toSet()
        )
      }
    }
  }

  /**
   * Takes an artifact version, plus information about the type of artifact, and constructs a summary view.
   * This should be supplemented/re-written to use actual data from stash/git/etc instead of parsing everything
   * from the version string.
   */
  private fun versionToSummary(
    artifact: DeliveryArtifact,
    version: String,
    environments: Set<ArtifactSummaryInEnvironment>
  ): ArtifactVersionSummary =
    when (artifact.type) {
      deb -> {
        val appversion = AppVersion.parseName(version)
        ArtifactVersionSummary(
          version = version,
          environments = environments,
          displayName = appversion?.version ?: version.removePrefix("${artifact.name}-"),
          build = if (appversion != null) BuildMetadata(id = appversion.buildNumber.toInt()) else null,
          git = if (appversion != null) GitMetadata(commit = appversion.commit) else null
        )
      }
      docker -> {
        var build: BuildMetadata? = null
        var git: GitMetadata? = null
        val dockerArtifact = artifact as DockerArtifact
        if (dockerArtifact.hasBuild()) {
          // todo eb: this could be less brittle
          val regex = Regex("""^.*-h(\d+).*$""")
          val result = regex.find(version)
          if (result != null && result.groupValues.size == 2) {
            build = BuildMetadata(id = result.groupValues[1].toInt())
          }
        }
        if (dockerArtifact.hasCommit()) {
          // todo eb: this could be less brittle
          git = GitMetadata(commit = version.substringAfterLast("."))
        }
        ArtifactVersionSummary(
          version = version,
          environments = environments,
          displayName = version,
          build = build,
          git = git
        )
      }
    }

  fun getFirstDeliveryConfigFor(application: String): DeliveryConfig? =
    repository.getDeliveryConfigsByApplication(application).also {
      if (it.size > 1) {
        log.warn("Application $application has ${it.size} delivery configs. " +
          "Returning the first one: ${it.first().name}.")
      }
    }.firstOrNull()

  private val ArtifactVersions.key: String
    get() = "${type.name}:$name"
}
