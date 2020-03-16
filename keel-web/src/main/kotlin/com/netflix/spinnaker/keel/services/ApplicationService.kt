package com.netflix.spinnaker.keel.services

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.core.api.ArtifactSummary
import com.netflix.spinnaker.keel.core.api.ArtifactSummaryInEnvironment
import com.netflix.spinnaker.keel.core.api.ArtifactVersionSummary
import com.netflix.spinnaker.keel.core.api.ArtifactVersions
import com.netflix.spinnaker.keel.core.api.EnvironmentSummary
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.PromotionStatus
import com.netflix.spinnaker.keel.persistence.ResourceStatus
import com.netflix.spinnaker.keel.persistence.ResourceSummary
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Service object that offers high-level APIs for application-related operations.
 */
@Component
class ApplicationService(
  private val pauser: ActuationPauser,
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
  fun getResourceSummariesFor(application: String): List<ResourceSummary> {
    var resources = repository.getSummaryByApplication(application)

    resources = resources.map { summary ->
      if (pauser.resourceIsPaused(summary.id)) {
        // we only update the status if the individual resource is paused,
        // because the application pause is reflected in the response as a top level key.
        summary.copy(status = ResourceStatus.PAUSED)
      } else {
        summary
      }
    }

    return resources
  }

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

        ArtifactVersionSummary(
          version = version,
          environments = artifactSummariesInEnvironments.toSet()
        )
      }.let { artifactVersionSummaries ->
        ArtifactSummary(
          name = artifact.name,
          type = artifact.type,
          versions = artifactVersionSummaries.toSet()
        )
      }
    }
  }

  private fun getFirstDeliveryConfigFor(application: String): DeliveryConfig? =
    repository.getDeliveryConfigsByApplication(application).also {
      if (it.size > 1) {
        log.warn("Application $application has ${it.size} delivery configs. " +
          "Returning the first one: ${it.first().name}.")
      }
    }.firstOrNull()

  private val ArtifactVersions.key: String
    get() = "${type.name}:$name"
}
