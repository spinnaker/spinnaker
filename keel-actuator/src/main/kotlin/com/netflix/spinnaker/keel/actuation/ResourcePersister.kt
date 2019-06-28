package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.api.name
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.events.CreateEvent
import com.netflix.spinnaker.keel.events.DeleteEvent
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceUpdated
import com.netflix.spinnaker.keel.persistence.NoSuchResourceException
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.get
import com.netflix.spinnaker.keel.plugin.ResolvableResourceHandler
import com.netflix.spinnaker.keel.plugin.supporting
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class ResourcePersister(
  private val resourceRepository: ResourceRepository,
  private val handlers: List<ResolvableResourceHandler<*, *>>,
  private val clock: Clock,
  private val publisher: ApplicationEventPublisher
) {
  fun upsert(resource: SubmittedResource<Any>): Resource<out Any> =
    handlers.supporting(resource.apiVersion, resource.kind)
      .normalize(resource)
      .also {
        if (it.name.isRegistered()) {
          return update(it.name, resource)
        } else {
          return create(resource)
        }
      }

  fun create(resource: SubmittedResource<Any>): Resource<out Any> =
    handlers.supporting(resource.apiVersion, resource.kind)
      .normalize(resource)
      .also {
        log.debug("Creating $it")
        resourceRepository.store(it)
        resourceRepository.appendHistory(ResourceCreated(it, clock))
        publisher.publishEvent(CreateEvent(it.name))
      }

  fun update(name: ResourceName, updated: SubmittedResource<Any>): Resource<out Any> {
    log.debug("Updating $name")
    val handler = handlers.supporting(updated.apiVersion, updated.kind)
    val existing = resourceRepository.get(name, Any::class.java)
    val resource = existing.copy(spec = updated.spec)
    val normalized = handler.normalize(resource)

    val diff = ResourceDiff(normalized.spec, existing.spec)

    return if (diff.hasChanges()) {
      log.debug("Resource {} updated: {}", normalized.name, diff.toDebug())
      normalized
        .also {
          resourceRepository.store(it)
          resourceRepository.appendHistory(ResourceUpdated(it, diff.toUpdateJson(), clock))
          resourceRepository.markCheckDue(it)
        }
    } else {
      existing
    }
  }

  fun delete(name: ResourceName): Resource<out Any> =
    resourceRepository
      .get<Any>(name)
      .also {
        resourceRepository.delete(name)
        publisher.publishEvent(DeleteEvent(name))
      }

  private fun ResourceName.isRegistered(): Boolean {
    try {
      resourceRepository.get(this, Any::class.java)
      return true
    } catch (e: NoSuchResourceException) {
      return false
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
