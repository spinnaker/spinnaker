package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.resources
import com.netflix.spinnaker.keel.events.ResourceDeleted
import java.time.Clock
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation.REQUIRED
import org.springframework.transaction.annotation.Transactional

/**
 * A class used to orchestrate the deletion of delivery configs and their associated resources
 */
@Component
class Cleaner(
  private val deliveryConfigRepository: DeliveryConfigRepository,
  private val artifactRepository: ArtifactRepository,
  private val resourceRepository: ResourceRepository,
  private val clock: Clock,
  private val publisher: ApplicationEventPublisher
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  /**
   * Deletes a delivery config and everything in it.
   */
  @Transactional(propagation = REQUIRED)
  fun delete(deliveryConfigName: String): DeliveryConfig {
    val deliveryConfig = deliveryConfigRepository.get(deliveryConfigName)

    deliveryConfig.environments.forEach { environment ->
      environment.resources.forEach { resource ->
        // resources must be removed from the environment then deleted
        deliveryConfigRepository.deleteResourceFromEnv(deliveryConfig.name, environment.name, resource.id)
        deleteResource(resource.id)
      }
      deliveryConfigRepository.deleteEnvironment(deliveryConfig.name, environment.name)
    }

    deliveryConfig.artifacts.forEach { artifact ->
      artifactRepository.delete(artifact)
    }

    deliveryConfigRepository.delete(deliveryConfig.name)

    return deliveryConfig
  }

  /**
   * Removes artifacts, environments, and resources that were present in the [old]
   * delivery config and are not present in the [new] delivery config
   */
  fun removeResources(old: DeliveryConfig, new: DeliveryConfig) {
    val newResources = new.resources.map { it.id }
    old.artifacts.forEach { artifact ->
      if (artifact !in new.artifacts) {
        log.debug("Updating config ${new.name}: removing artifact $artifact")
        artifactRepository.delete(artifact)
      }
    }

    old.environments
      .forEach { environment ->
        environment.resources.forEach { resource ->
          if (resource.id !in newResources) {
            log.debug("Updating config ${new.name}: removing resource ${resource.id} in environment ${environment.name}")
            deliveryConfigRepository.deleteResourceFromEnv(
              deliveryConfigName = old.name, environmentName = environment.name, resourceId = resource.id
            )
            deleteResource(resource.id)
          }
        }
        if (environment.name !in new.environments.map { it.name }) {
          log.debug("Updating config ${new.name}: removing environment ${environment.name}")
          deliveryConfigRepository.deleteEnvironment(new.name, environment.name)
        }
      }
  }

  fun deleteResource(id: String): Resource<out ResourceSpec> =
    resourceRepository
      .get<ResourceSpec>(id)
      .also {
        resourceRepository.delete(id)
        publisher.publishEvent(ResourceDeleted(it, clock))
      }
}
