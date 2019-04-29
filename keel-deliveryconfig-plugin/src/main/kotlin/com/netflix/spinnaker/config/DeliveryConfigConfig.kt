package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.annealing.ResourcePersister
import com.netflix.spinnaker.keel.deliveryconfig.resource.DeliveryConfigHandler
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.plugin.ResourceNormalizer
import org.springframework.beans.factory.ObjectFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty("keel.plugins.deliveryConfig.enabled")
// I wasn't sure I hated the name DeliveryConfig until this happened
class DeliveryConfigConfig {

  @Bean
  fun deliveryConfigHandler(
    objectMapper: ObjectMapper,
    normalizers: List<ResourceNormalizer<*>>,
    resourceRepository: ObjectFactory<ResourceRepository>,
    resourcePersister: ObjectFactory<ResourcePersister>
  ) = DeliveryConfigHandler(
      objectMapper,
      normalizers,
      resourcePersister,
      resourceRepository)
}
