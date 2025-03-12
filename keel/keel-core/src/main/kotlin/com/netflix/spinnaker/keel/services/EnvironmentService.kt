package com.netflix.spinnaker.keel.services

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.core.api.PromotionStatus
import com.netflix.spinnaker.keel.core.api.PromotionStatus.CURRENT
import com.netflix.spinnaker.keel.core.api.ResourceSummary
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.NoSuchDeliveryConfigException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * High level APIs for the environment centric view.
 *
 * These APIs give a view of what's currently happening in an env,
 * and do not expose past data.
 */
@Component
class EnvironmentService(
  private val repository: KeelRepository,
  private val applicationService: ApplicationService,
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  fun getEnvironmentViews(application: String): List<EnvironmentView> {
    val deliveryConfig = try {
      repository.getDeliveryConfigForApplication(application)
    } catch (e: NoSuchDeliveryConfigException) {
      return emptyList()
    }

    val resourceSummaries = applicationService.getResourceSummaries(deliveryConfig)

    return deliveryConfig.environments.map { environment ->
      val currentArtifacts = try {
        repository.getArtifactVersionsByStatus(deliveryConfig, environment.name, listOf(CURRENT)).toSet()
      } catch (e: Exception) {
        log.error("Failed to get current artifacts for $application: ", e)
        emptySet()
      }

      EnvironmentView(
        environment,
        resourceSummaries.filter { environment.resourceIds.contains(it.id) },
        currentArtifacts
      )
    }
  }
}

/**
 * Preliminary data class to hold data for each environment.
 * This will grow as we add functionality and move to its own file.
 */
data class EnvironmentView(
  @JsonIgnore val environment: Environment,
  val resourcesStatus: List<ResourceSummary>,
  val currentArtifacts: Set<PublishedArtifact>,
) {
  val name: String
    get() = environment.name
}

