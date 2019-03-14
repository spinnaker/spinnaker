package com.netflix.spinnaker.keel.annealing

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceDeleted
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.events.ResourceUpdated
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.get
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.supporting
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ResourcePersister(
  private val resourceRepository: ResourceRepository,
  private val handlers: List<ResourceHandler<*>>,
  private val queue: ResourceCheckQueue
) {
  fun handle(event: ResourceEvent): Resource<*> {
    log.info("Received event {}", event)
    return when (event) {
      is ResourceCreated ->
        handlers.supporting(event.resource.apiVersion, event.resource.kind)
          .normalize(event.resource)
          .also(resourceRepository::store)
          .also { queue.scheduleCheck(it) }
      is ResourceUpdated ->
        handlers.supporting(event.resource.apiVersion, event.resource.kind)
          .normalize(event.resource)
          .also(resourceRepository::store)
          .also { queue.scheduleCheck(it) }
      is ResourceDeleted -> {
        resourceRepository.delete(event.name)
        resourceRepository.get<Any>(event.name).also {
          queue.scheduleCheck(it)
        }
      }
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
