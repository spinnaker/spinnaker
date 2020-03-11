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
   *
   * The algorithm is as follows:
   *
   * For each environment in the delivery config:
   * |    For each artifact in the environment:
   * |    |    Build a map of artifact versions by state (e.g. pending, current, previous, etc.).
   * |    |    For each pair of (state, versions):
   * |    |    |    For each version in this state:
   * |    |    |    |    Get and cache an [ArtifactSummaryInEnvironment] for this version in this environment.
   * |    |    |    |    Create and cache the [ArtifactVersionSummary] for this version.
   * |    |    Finally, create the [ArtifactSummary] by looking up the "inner" summaries that were built above.
   * Return a flat list of all the artifact summaries for all environments in the delivery config.
   */
  fun getArtifactSummariesFor(application: String): List<ArtifactSummary> {
    val deliveryConfig = getFirstDeliveryConfigFor(application)
      ?: throw InvalidRequestException("No delivery config found for application $application")

    val environmentSummaries = getEnvironmentSummariesFor(application)

    // map of environments to the set of artifact summaries by state
    val artifactSummariesByEnvironmentAndState = environmentSummaries.associate { environmentSummary ->
      environmentSummary.name to mapOf(
        "current" to mutableSetOf<ArtifactSummaryInEnvironment>(),
        "deploying" to mutableSetOf<ArtifactSummaryInEnvironment>(),
        "pending" to mutableSetOf<ArtifactSummaryInEnvironment>(),
        "approved" to mutableSetOf<ArtifactSummaryInEnvironment>(),
        "previous" to mutableSetOf<ArtifactSummaryInEnvironment>(),
        "vetoed" to mutableSetOf<ArtifactSummaryInEnvironment>()
      )
    }

    val versionSummariesByArtifact = environmentSummaries.flatMap { environmentSummary ->
      environmentSummary.artifacts.map { artifact ->
        artifact.key to mutableSetOf<ArtifactVersionSummary>()
      }
    }.toMap()

    val artifactSummaries = environmentSummaries.flatMap { environmentSummary ->

      environmentSummary.artifacts.map { artifact ->
        val versionsByState = mapOf(
          "current" to artifact.versions.current?.let { listOf(it) }.orEmpty(),
          "deploying" to artifact.versions.deploying?.let { listOf(it) }.orEmpty(),
          "pending" to artifact.versions.pending,
          "approved" to artifact.versions.approved,
          "previous" to artifact.versions.previous,
          "vetoed" to artifact.versions.vetoed
        )

        versionsByState.entries.map {
          val state = it.key
          val versions = it.value

          versionSummariesByArtifact[artifact.key]!!.addAll(
            versions.map { version ->
              val summaryInEnvironment = artifactRepository.getArtifactSummaryInEnvironment(
                deliveryConfig = deliveryConfig,
                environmentName = environmentSummary.name,
                artifactName = artifact.name,
                artifactType = artifact.type,
                version = version
              )

              ArtifactVersionSummary(
                version = version,
                environments = artifactSummariesByEnvironmentAndState
                  .get(environmentSummary.name)!! // safe because we create the maps with these keys above
                  .get(state)!! // safe because we create the maps with these keys above
                  .also { artifactSummariesInEnvironment ->
                    if (summaryInEnvironment != null) {
                      artifactSummariesInEnvironment.add(summaryInEnvironment)
                    }
                  }
              )
            }
          )
        }
        // finally, create the artifact summary by looking up the version summaries that were built above
        ArtifactSummary(
          name = artifact.name,
          type = artifact.type,
          versions = versionSummariesByArtifact[artifact.key]!!.toSet()
        )
      }
    }

    return artifactSummaries
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
