package com.netflix.spinnaker.keel.annealing

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceUpdated
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.get
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.supporting
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class ResourcePersister(
  private val resourceRepository: ResourceRepository,
  private val handlers: List<ResourceHandler<*>>,
  private val queue: ResourceCheckQueue,
  private val publisher: ApplicationEventPublisher,
  private val clock: Clock
) {
  fun create(resource: SubmittedResource<Any>): Resource<out Any> =
    handlers.supporting(resource.apiVersion, resource.kind)
      .normalize(resource)
      .also(resourceRepository::store)
      .also { resourceRepository.appendHistory(ResourceCreated(it, clock)) }
      .also { queue.scheduleCheck(it) }

  fun update(resource: Resource<Any>): Resource<out Any> {
    val existing = resourceRepository.get<Any>(resource.metadata.uid)

    return if (existing.spec == resource.spec) {
      existing
    } else {
      handlers.supporting(resource.apiVersion, resource.kind)
        .normalize(resource)
        .also(resourceRepository::store)
        .also { resourceRepository.appendHistory(ResourceUpdated(it, clock)) }
        .also { queue.scheduleCheck(it) }
    }
  }

  fun delete(name: ResourceName): Resource<out Any> =
    resourceRepository
      .get<Any>(name)
      .also { resourceRepository.delete(name) }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
