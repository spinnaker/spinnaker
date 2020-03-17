package com.netflix.spinnaker.keel.services

import com.netflix.spinnaker.keel.api.ComputeResourceSpec
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.plugins.supportingComputeResources
import com.netflix.spinnaker.keel.core.api.ArtifactSummary
import com.netflix.spinnaker.keel.core.api.ArtifactSummaryInEnvironment
import com.netflix.spinnaker.keel.core.api.ArtifactVersionSummary
import com.netflix.spinnaker.keel.core.api.ArtifactVersions
import com.netflix.spinnaker.keel.core.api.EnvironmentSummary
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.PromotionStatus
import com.netflix.spinnaker.keel.persistence.ResourceArtifactSummary
import com.netflix.spinnaker.keel.persistence.ResourceSummary
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Service object that offers high-level APIs for application-related operations.
 */
@Component
class ApplicationService(
  private val resolvers: List<Resolver<ComputeResourceSpec>>,
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
      summary.resource.let { resource ->
        if (resource.spec is ComputeResourceSpec) {
          resolvers.supportingComputeResources(resource as Resource<ComputeResourceSpec>)
            .fold(resource) { computeResource, resolver ->
              log.debug("Applying ${resolver.javaClass.simpleName} to ${computeResource.id}")
              resolver(computeResource)
            }.let { computeResource ->
              if (computeResource.spec.deliveryArtifact != null && computeResource.spec.artifactVersion != null) {
                summary.copy(artifact = ResourceArtifactSummary(
                  name = computeResource.spec.deliveryArtifact!!.name,
                  type = computeResource.spec.deliveryArtifact!!.type,
                  desiredVersion = computeResource.spec.artifactVersion!!
                ))
              } else {
                summary
              }
            }
        } else {
          summary
        }
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
