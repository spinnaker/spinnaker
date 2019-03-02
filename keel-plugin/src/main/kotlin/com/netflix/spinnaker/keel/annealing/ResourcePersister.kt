package com.netflix.spinnaker.keel.annealing

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.events.ResourceEventType.CREATE
import com.netflix.spinnaker.keel.events.ResourceEventType.DELETE
import com.netflix.spinnaker.keel.events.ResourceEventType.UPDATE
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.supporting
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
        handlers.supporting(event.resource.apiVersion, event.resource.kind)
          .validate(event.resource, true)
          .also(resourceRepository::store)
          .also { publisher.publishEvent(ResourceCheckEvent(it)) }
      UPDATE ->
        handlers.supporting(event.resource.apiVersion, event.resource.kind)
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
}
