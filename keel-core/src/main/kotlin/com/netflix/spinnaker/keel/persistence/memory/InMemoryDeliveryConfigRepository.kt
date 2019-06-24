package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.NoSuchDeliveryConfigName
import com.netflix.spinnaker.keel.persistence.ResourceRepository

class InMemoryDeliveryConfigRepository(
  private val artifactRepository: ArtifactRepository,
  private val resourceRepository: ResourceRepository
) : DeliveryConfigRepository {
  private val configs = mutableMapOf<String, DeliveryConfig>()

  override fun get(name: String): DeliveryConfig =
    configs[name] ?: throw NoSuchDeliveryConfigName(name)

  override fun store(deliveryConfig: DeliveryConfig) {
    configs[deliveryConfig.name] = deliveryConfig
    deliveryConfig
      .artifacts
      .forEach { artifact ->
        artifactRepository.register(artifact)
      }
    deliveryConfig
      .environments
      .flatMap { it.resources }
      .forEach { resource ->
        resourceRepository.store(resource)
      }
  }
}
