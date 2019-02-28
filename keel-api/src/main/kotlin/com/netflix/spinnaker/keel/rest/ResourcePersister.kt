package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.events.ResourceEventType.*
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.monitoring.UnsupportedKind
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ResourcePersister(
  private val resourceRepository: ResourceRepository,
  private val handlers: List<ResourceHandler<*>>
) {

  fun handle(event: ResourceEvent): Resource<*> {
    log.info("Received event {}", event)
    when (event.type) {
      CREATE,
      UPDATE -> {
        val handler = pluginFor(event.resource.apiVersion, event.resource.kind) ?: throw UnsupportedKind(event.resource.apiVersion, event.resource.kind)
        val validatedResource = handler.validateAndName(event.resource)
        resourceRepository.store(validatedResource)
        return validatedResource
      }
      DELETE -> {
        resourceRepository.delete(event.resource.metadata.name)
        return event.resource
      }
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private fun pluginFor(apiVersion: ApiVersion, singular: String): ResourceHandler<*>? =
    handlers.find { it.apiVersion == apiVersion && it.supportedKind.first.singular == singular }
}
