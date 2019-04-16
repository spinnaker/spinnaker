package com.netflix.spinnaker.keel.annealing

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.diff.toDebug
import com.netflix.spinnaker.keel.diff.toJson
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceUpdated
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.get
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.supporting
import de.danielbechler.diff.ObjectDifferBuilder
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
  private val differ = ObjectDifferBuilder.buildDefault()

  fun create(resource: SubmittedResource<Any>): Resource<out Any> =
    handlers.supporting(resource.apiVersion, resource.kind)
      .normalize(resource)
      .also(resourceRepository::store)
      .also { resourceRepository.appendHistory(ResourceCreated(it, clock)) }
      .also { queue.scheduleCheck(it) }

  fun update(resource: Resource<Any>): Resource<out Any> {
    val normalized = handlers.supporting(resource.apiVersion, resource.kind)
      .normalize(resource)

    val existing = resourceRepository.get(normalized.metadata.uid, normalized.spec.javaClass)

    val diff = differ.compare(normalized.spec, existing.spec)
    return if (diff.hasChanges()) {
      log.debug("Resource {} updated: {}", normalized.metadata.name, diff.toDebug(normalized.spec, existing.spec))
      normalized
        .also(resourceRepository::store)
        .also { resourceRepository.appendHistory(ResourceUpdated(it, diff.toJson(normalized.spec, existing.spec), clock)) }
        .also { queue.scheduleCheck(it) }
    } else {
      existing
    }
  }

  fun delete(name: ResourceName): Resource<out Any> =
    resourceRepository
      .get<Any>(name)
      .also { resourceRepository.delete(name) }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
