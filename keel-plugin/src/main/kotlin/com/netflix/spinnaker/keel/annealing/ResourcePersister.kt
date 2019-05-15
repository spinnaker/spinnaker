package com.netflix.spinnaker.keel.annealing

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
  private val resourceActuator: ResourceActuator,
  private val clock: Clock
) {
  private val differ = ObjectDifferBuilder.buildDefault()

  fun create(resource: SubmittedResource<Any>): Resource<out Any> =
    handlers.supporting(resource.apiVersion, resource.kind)
      .normalize(resource)
      .also(resourceRepository::store)
      .also { resourceRepository.appendHistory(ResourceCreated(it, clock)) }
      .also { resourceActuator.checkResource(it.metadata.name, it.apiVersion, it.kind) }

  fun update(resource: Resource<Any>): Resource<out Any> {
    val normalized = handlers.supporting(resource.apiVersion, resource.kind)
      .normalize(resource)

    val existing = resourceRepository.get(normalized.metadata.uid, normalized.spec.javaClass)

    val diff = differ.compare(normalized.spec, existing.spec)
    return if (diff.hasChanges()) {
      log.debug("Resource {} updated: {}", normalized.metadata.name, diff.toDebug(normalized.spec, existing.spec))
      normalized
        .also(resourceRepository::store)
        .also { resourceRepository.appendHistory(ResourceUpdated(it, diff.toUpdateJson(it.spec, existing.spec), clock)) }
        .also { resourceActuator.checkResource(it.metadata.name, it.apiVersion, it.kind) }
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
