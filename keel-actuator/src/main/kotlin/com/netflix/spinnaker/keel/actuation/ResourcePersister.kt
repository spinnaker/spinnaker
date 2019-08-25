package com.netflix.spinnaker.keel.actuation

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.api.name
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceDeleted
import com.netflix.spinnaker.keel.events.ResourceUpdated
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.NoSuchResourceException
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.get
import com.netflix.spinnaker.keel.plugin.ResolvableResourceHandler
import com.netflix.spinnaker.keel.plugin.supporting
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation.REQUIRED
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Component
class ResourcePersister(
  private val deliveryConfigRepository: DeliveryConfigRepository,
  private val artifactRepository: ArtifactRepository,
  private val resourceRepository: ResourceRepository,
  private val handlers: List<ResolvableResourceHandler<*, *>>,
  private val clock: Clock,
  private val publisher: ApplicationEventPublisher,
  private val objectMapper: ObjectMapper
) {
  @Transactional(propagation = REQUIRED)
  fun upsert(deliveryConfig: SubmittedDeliveryConfig): DeliveryConfig =
    DeliveryConfig(
      name = deliveryConfig.name,
      application = deliveryConfig.application,
      artifacts = deliveryConfig.artifacts,
      environments = deliveryConfig.environments.mapTo(mutableSetOf()) { env ->
        Environment(
          name = env.name,
          resources = env.resources.mapTo(mutableSetOf()) { resource ->
            upsert(resource)
          }
        )
      }
    )
      .also {
        it.artifacts.forEach { artifact ->
          artifact.register()
        }
        deliveryConfigRepository.store(it)
      }

  fun <T : ResourceSpec> upsert(resource: SubmittedResource<T>): Resource<T> =
      resource.let {
        if (it.name.isRegistered()) {
          update(it.name, it)
        } else {
          create(it)
        }
      }

  fun <T : ResourceSpec> create(resource: SubmittedResource<T>): Resource<T> =
    handlerFor(resource)
      .normalize(resource)
      .also {
        log.debug("Creating $it")
        resourceRepository.store(it)
        publisher.publishEvent(ResourceCreated(it, clock))
      }

  @Suppress("UNCHECKED_CAST")
  private fun <T : ResourceSpec> handlerFor(resource: SubmittedResource<T>) =
    handlers.supporting(
        resource.apiVersion,
        resource.kind
      ) as ResolvableResourceHandler<T, *>

  fun <T : ResourceSpec> update(name: ResourceName, updated: SubmittedResource<T>): Resource<T> {
    log.debug("Updating $name")
    val handler = handlerFor(updated)
    @Suppress("UNCHECKED_CAST")
    val existing = resourceRepository.get(name) as Resource<T>
    val resource = existing.withSpec(updated.spec, handler.supportedKind.second)
    val normalized = handler.normalize(resource)

    val diff = ResourceDiff(normalized.spec, existing.spec)

    return if (diff.hasChanges()) {
      log.debug("Resource {} updated: {}", normalized.name, diff.toDebug())
      normalized
        .also {
          resourceRepository.store(it)
          publisher.publishEvent(ResourceUpdated(it, diff.toUpdateJson(), clock))
        }
    } else {
      existing
    }
  }

  private fun <T : ResourceSpec> Resource<T>.withSpec(spec: Any, type: Class<out ResourceSpec>): Resource<T> {
    check(type.isAssignableFrom(spec.javaClass)) {
      "Spec type is incorrect: expected ${type.simpleName} but found ${spec.javaClass.simpleName}"
    }
    @Suppress("UNCHECKED_CAST")
    return copy(spec = spec as T)
  }

  fun delete(name: ResourceName): Resource<out ResourceSpec> =
    resourceRepository
      .get<ResourceSpec>(name)
      .also {
        resourceRepository.delete(name)
        publisher.publishEvent(ResourceDeleted(it, clock))
      }

  private fun ResourceName.isRegistered(): Boolean =
    try {
      resourceRepository.get(this)
      true
    } catch (e: NoSuchResourceException) {
      false
    }

  private fun DeliveryArtifact.register() {
    if (!artifactRepository.isRegistered(name, type)) {
      artifactRepository.register(this)
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
