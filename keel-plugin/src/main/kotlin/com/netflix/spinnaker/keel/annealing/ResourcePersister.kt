package com.netflix.spinnaker.keel.annealing

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.events.ResourceEventType.CREATE
import com.netflix.spinnaker.keel.events.ResourceEventType.DELETE
import com.netflix.spinnaker.keel.events.ResourceEventType.UPDATE
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class ResourcePersister(
  private val resourceRepository: ResourceRepository,
  private val handlers: List<ResourceHandler<*>>,
  private val publisher: ApplicationEventPublisher
) {
  fun handle(event: ResourceEvent): Resource<*> {
    log.info("Received event {}", event)
    return when (event.type) {
      CREATE ->
        pluginFor(event.resource.apiVersion, event.resource.kind)
          .validate(event.resource, true)
          .also(resourceRepository::store)
          .also { publisher.publishEvent(ResourceCheckEvent(it)) }
      UPDATE ->
        pluginFor(event.resource.apiVersion, event.resource.kind)
          .validate(event.resource, false)
          .also(resourceRepository::store)
          .also { publisher.publishEvent(ResourceCheckEvent(it)) }
      DELETE ->
        event.resource.also {
          resourceRepository.delete(it.metadata.name)
          publisher.publishEvent(ResourceCheckEvent(it))
        }
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private fun pluginFor(apiVersion: ApiVersion, kind: String): ResourceHandler<*> =
    handlers
      .find {
        it.apiVersion == apiVersion && it.supportedKind.first.singular == kind
      }
      ?: throw UnsupportedKind(apiVersion, kind)
}
