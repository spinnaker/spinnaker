package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceName
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
  private val publisher: ApplicationEventPublisher
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

  fun upsert(resource: SubmittedResource<*>): Resource<out Any> =
    handlers.supporting(resource.apiVersion, resource.kind)
      .normalize(resource)
      .let {
        if (it.name.isRegistered()) {
          update(it.name, resource)
        } else {
          create(resource)
        }
      }

  fun create(resource: SubmittedResource<*>): Resource<out Any> =
    handlers.supporting(resource.apiVersion, resource.kind)
      .normalize(resource)
      .also {
        log.debug("Creating $it")
        resourceRepository.store(it)
        publisher.publishEvent(ResourceCreated(it, clock))
      }

  fun update(name: ResourceName, updated: SubmittedResource<*>): Resource<out Any> {
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
          publisher.publishEvent(ResourceUpdated(it, diff.toUpdateJson(), clock))
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
        publisher.publishEvent(ResourceDeleted(it, clock))
      }

  private fun ResourceName.isRegistered(): Boolean =
    try {
      resourceRepository.get(this, Any::class.java)
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
