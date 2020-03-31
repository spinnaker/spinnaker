package com.netflix.spinnaker.keel.test

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository

/**
 * Helper functions for working with delivery configs
 */

fun deliveryConfig(
  resource: Resource<*> = resource(),
  env: Environment = Environment("test", setOf(resource)),
  configName: String = "myconfig",
  artifact: DeliveryArtifact = DebianArtifact(name = "fnord", deliveryConfigName = configName, vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2"))),
  deliveryConfig: DeliveryConfig = DeliveryConfig(
    name = configName,
    application = "fnord",
    serviceAccount = "keel@spinnaker",
    artifacts = setOf(artifact),
    environments = setOf(env)
  )
): DeliveryConfig {
  return deliveryConfig
}

fun saveDeliveryConfig(
  deliveryConfig: DeliveryConfig,
  artifactRepository: InMemoryArtifactRepository,
  resourceRepository: InMemoryResourceRepository,
  deliveryConfigRepository: InMemoryDeliveryConfigRepository
) {
  deliveryConfigRepository.store(deliveryConfig)
  deliveryConfig.environments.flatMap { it.resources }.forEach {
    resourceRepository.store(it)
  }
  deliveryConfig.artifacts.forEach {
    artifactRepository.register(it)
  }
}
