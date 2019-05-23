package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.diff.toDebug
import com.netflix.spinnaker.keel.diff.toUpdateJson
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceUpdated
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.get
import com.netflix.spinnaker.keel.plugin.ResolvableResourceHandler
import com.netflix.spinnaker.keel.plugin.supporting
import de.danielbechler.diff.ObjectDifferBuilder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class ResourcePersister(
  private val resourceRepository: ResourceRepository,
  private val handlers: List<ResolvableResourceHandler<*, *>>,
  private val clock: Clock
) {
  private val differ = ObjectDifferBuilder.buildDefault()

  fun create(resource: SubmittedResource<Any>): Resource<out Any> =
    handlers.supporting(resource.apiVersion, resource.kind)
      .normalize(resource)
      .also {
        resourceRepository.store(it)
        resourceRepository.appendHistory(ResourceCreated(it, clock))
      }

  fun update(name: ResourceName, updated: SubmittedResource<Any>): Resource<out Any> {
    val handler = handlers.supporting(updated.apiVersion, updated.kind)
    val existing = resourceRepository.get(name, Any::class.java)
    val resource = existing.copy(spec = updated.spec)
    val normalized = handler.normalize(resource)

    val diff = differ.compare(normalized.spec, existing.spec)
    return if (diff.hasChanges()) {
      log.debug("Resource {} updated: {}", normalized.metadata.name, diff.toDebug(normalized.spec, existing.spec))
      normalized
        .also {
          resourceRepository.store(it)
          resourceRepository.appendHistory(ResourceUpdated(it, diff.toUpdateJson(it.spec, existing.spec), clock))
          resourceRepository.markCheckDue(it)
        }
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
