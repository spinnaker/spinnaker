package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.NoSuchDeliveryConfigName

class InMemoryDeliveryConfigRepository(
  private val artifactRepository: ArtifactRepository
) : DeliveryConfigRepository {
  private val configs = mutableMapOf<String, DeliveryConfig>()

  override fun get(name: String): DeliveryConfig =
    configs[name] ?: throw NoSuchDeliveryConfigName(name)

  override fun store(deliveryConfig: DeliveryConfig) {
    with(deliveryConfig) {
      configs[name] = this
      artifacts.forEach {
        if (!artifactRepository.isRegistered(it.name, it.type)) {
          artifactRepository.register(it)
        }
      }
    }
  }
}
