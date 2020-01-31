package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.DebianArtifact
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.DockerArtifact
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.normalize
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.events.ArtifactRegisteredEvent
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceDeleted
import com.netflix.spinnaker.keel.events.ResourceUpdated
import com.netflix.spinnaker.keel.exceptions.UnsupportedArtifactTypeException
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.Cleaner
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.NoSuchDeliveryConfigException
import com.netflix.spinnaker.keel.persistence.NoSuchResourceException
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.get
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.supporting
import java.time.Clock
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation.REQUIRED
import org.springframework.transaction.annotation.Transactional

@Component
class ResourcePersister(
  private val deliveryConfigRepository: DeliveryConfigRepository,
  private val artifactRepository: ArtifactRepository,
  private val resourceRepository: ResourceRepository,
  private val handlers: List<ResourceHandler<*, *>>,
  private val cleaner: Cleaner,
  private val clock: Clock,
  private val publisher: ApplicationEventPublisher
) {
  @Transactional(propagation = REQUIRED)
  fun upsert(deliveryConfig: SubmittedDeliveryConfig): DeliveryConfig {
    val old = try {
      deliveryConfigRepository.get(deliveryConfig.name)
    } catch (e: NoSuchDeliveryConfigException) {
      null
    }

    val new = DeliveryConfig(
      name = deliveryConfig.name,
      application = deliveryConfig.application,
      serviceAccount = deliveryConfig.serviceAccount,
      artifacts = deliveryConfig.artifacts.transform(deliveryConfig.name),
      environments = deliveryConfig.environments.mapTo(mutableSetOf()) { env ->
        Environment(
          name = env.name,
          resources = env.resources.mapTo(mutableSetOf()) { resource ->
            upsert(
              resource.copy(
                metadata = mapOf("serviceAccount" to deliveryConfig.serviceAccount) + resource.metadata
              )
            )
          },
          constraints = env.constraints,
          notifications = env.notifications
        )
      }
    )
    new.artifacts.forEach { artifact ->
      artifact.register()
    }
    deliveryConfigRepository.store(new)
    if (old != null) {
      cleaner.removeResources(old, new)
    }
    return new
  }

  fun deleteDeliveryConfig(deliveryConfigName: String) {
    cleaner.delete(deliveryConfigName)
  }

  fun <T : ResourceSpec> upsert(resource: SubmittedResource<T>): Resource<T> =
    resource.let {
      if (it.id.isRegistered()) {
        update(it.id, it)
      } else {
        create(it)
      }
    }

  fun <T : ResourceSpec> create(resource: SubmittedResource<T>): Resource<T> =
    resource
      .normalize()
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
    ) as ResourceHandler<T, *>

  fun <T : ResourceSpec> update(id: String, updated: SubmittedResource<T>): Resource<T> {
    log.debug("Updating $id")
    val handler = handlerFor(updated)
    @Suppress("UNCHECKED_CAST")
    val existing = resourceRepository.get(id) as Resource<T>
    val resource = existing.withSpec(updated.spec, handler.supportedKind.specClass)

    val diff = DefaultResourceDiff(resource.spec, existing.spec)

    return if (diff.hasChanges()) {
      log.debug("Resource {} updated: {}", resource.id, diff.toDebug())
      resource
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

  fun deleteResource(id: String): Resource<out ResourceSpec> =
    resourceRepository
      .get<ResourceSpec>(id)
      .also {
        resourceRepository.delete(id)
        publisher.publishEvent(ResourceDeleted(it, clock))
      }

  private fun String.isRegistered(): Boolean =
    try {
      resourceRepository.get(this)
      true
    } catch (e: NoSuchResourceException) {
      false
    }

  private fun DeliveryArtifact.register() {
    artifactRepository.register(this)
    publisher.publishEvent(ArtifactRegisteredEvent(this))
  }

  private fun Set<DeliveryArtifact>.transform(deliveryConfigName: String) =
    this.map { artifact ->
      when (artifact) {
        is DockerArtifact -> artifact.copy(deliveryConfigName = deliveryConfigName)
        is DebianArtifact -> artifact.copy(deliveryConfigName = deliveryConfigName)
        else -> throw UnsupportedArtifactTypeException(artifact.type.value())
      }
    }.toSet()

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
