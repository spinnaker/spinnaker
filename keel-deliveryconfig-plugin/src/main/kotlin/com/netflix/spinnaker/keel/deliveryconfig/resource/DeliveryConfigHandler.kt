package com.netflix.spinnaker.keel.deliveryconfig.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.annealing.ResourcePersister
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.api.deliveryconfig.ChildResource
import com.netflix.spinnaker.keel.api.deliveryconfig.DeliveryConfig
import com.netflix.spinnaker.keel.api.deliveryconfig.DeliveryEnvironment
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceUpdated
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.get
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.ResourceHandler.ResourceDiff
import com.netflix.spinnaker.keel.plugin.ResourceNormalizer
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectFactory

class DeliveryConfigHandler(
  override val objectMapper: ObjectMapper,
  override val normalizers: List<ResourceNormalizer<*>>,
  private val resourcePersisterProvider: ObjectFactory<ResourcePersister>,
  private val resourceRepositoryProvider: ObjectFactory<ResourceRepository>
) : ResourceHandler<DeliveryConfig> {
  override val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }

  override val apiVersion = SPINNAKER_API_V1.subApi("delivery-config")
  override val supportedKind = ResourceKind(
    apiVersion.group,
    "delivery-config",
    "delivery-configs"
  ) to DeliveryConfig::class.java

  override fun generateName(spec: DeliveryConfig) = ResourceName(
    "deliveryConfig:${spec.name}"
  )

  private val resourcePersister: ResourcePersister by lazy { resourcePersisterProvider.getObject() }
  private val resourceRepository: ResourceRepository by lazy { resourceRepositoryProvider.getObject() }

  private val DeliveryConfig.isResolved : Boolean
    get() = this.deliveryEnvironments.all { env ->
      env.packageRef.resourceMetadata != null
        && env.targets.all { it.resourceMetadata != null}
    }

  override fun current(resource: Resource<DeliveryConfig>): DeliveryConfig? =
    if (resource.spec.isResolved) {
      runBlocking {
        val cfg = resource.spec.copy(
          deliveryEnvironments = resource.spec.deliveryEnvironments.map { it.refreshFromRepository() })
        cfg
      }
    } else {
      null
    }

  override fun delete(resource: Resource<DeliveryConfig>) {
    TODO("Not implemented")
  }

  override fun upsert(resource: Resource<DeliveryConfig>, resourceDiff: ResourceDiff<DeliveryConfig>?) {
    runBlocking {
      resourceRepository.store(resource.copy(spec = resource.spec.copy(
        deliveryEnvironments = resource.spec.deliveryEnvironments.map { env ->
          env.packageRef.getOrCreateResource().let { pkg ->
            env.copy(
              packageRef = pkg,
              targets = env.targets.map { t ->
                t.getOrCreateResource().updateMetadata(mapOf("packageResource" to pkg))
              }
            )
          }
        }
      )))
    }
  }

  private fun DeliveryEnvironment.refreshFromRepository() =
    this.copy(
      packageRef = this.packageRef.refreshFromRepository(),
      targets = this.targets.map { t -> t.refreshFromRepository() }
    )

  private fun ChildResource.refreshFromRepository() =
    this.resourceMetadata?.let { meta ->
      resourceRepository.get<Map<String, Any?>>(meta.name).let { res ->
        this.copy(
          metadata = objectMapper.convertValue(res.metadata),
          spec = res.spec)
      }
    } ?: this

  private fun ChildResource.getOrCreateResource(): ChildResource =
    when (val resourceName = this.resourceMetadata?.name) {
      null -> this.createNew()
      else -> resourceName.getExisting()
    }

  private fun ChildResource.createNew() =
    resourcePersister
      .handle(ResourceCreated(this.asSubmittedResource()))
      .asBaseSubResource()


  private fun ResourceName.getExisting() =
    resourceRepository
      .get<Any>(this)
      .asBaseSubResource()

  private fun ChildResource.updateMetadata(extraMetadata: Map<String, Any?>) =
    resourcePersister
      .handle(ResourceUpdated(
        objectMapper.convertValue(
          this.copy(
            metadata = this.metadata?.let { it + extraMetadata }
          )))).asBaseSubResource()

  private fun ChildResource.asSubmittedResource() =
    objectMapper.convertValue<SubmittedResource<Any>>(
      this.copy(metadata = null)
    )

  private fun Resource<*>.asBaseSubResource() =
    ChildResource(
      apiVersion = this.apiVersion,
      kind = this.kind,
      metadata = objectMapper.convertValue(this.metadata),
      spec = objectMapper.convertValue(this.spec)
    )

}
