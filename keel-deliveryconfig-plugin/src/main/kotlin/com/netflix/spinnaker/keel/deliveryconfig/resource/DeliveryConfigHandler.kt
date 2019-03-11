package com.netflix.spinnaker.keel.deliveryconfig.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.deliveryconfig.DeliveryConfig
import com.netflix.spinnaker.keel.front50.Front50Service
import com.netflix.spinnaker.keel.front50.model.Delivery
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.retrofit.isNotFound
import de.danielbechler.diff.node.DiffNode
import de.huxhorn.sulky.ulid.ULID
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import retrofit2.HttpException

class DeliveryConfigHandler(
  private val front50Service: Front50Service,
  override val objectMapper: ObjectMapper
) : ResourceHandler<DeliveryConfig> {
  override val apiVersion = SPINNAKER_API_V1.subApi("delivery-config")
  override val supportedKind = ResourceKind(
    apiVersion.group,
    "delivery-config",
    "delivery-configs"
  ) to DeliveryConfig::class.java

  override fun generateName(spec: DeliveryConfig) = ResourceName(
    "deliveryConfig:${spec.name}"
  )

  override fun current(resource: Resource<DeliveryConfig>): DeliveryConfig? =
    runBlocking {
      log.debug("Fetching delivery ${resource.spec.name} from front50")
      try {
        val delivery = front50Service.deliveryById(resource.spec.name).await()
        log.debug("Fetched $delivery")
        DeliveryConfig(delivery.id, delivery.application)
      } catch (e: HttpException) {
        if (!e.isNotFound) {
          log.error("Fetching delivery from front50 failed", e)
          throw e
        }
        null
      }
    }


  override fun delete(resource: Resource<DeliveryConfig>) {
    runBlocking {
      log.debug("deleting delivery ${resource.spec.name} in application ${resource.spec.application}")
      front50Service.deleteDelivery(resource.spec.application, resource.spec.name).await()
      log.debug("deleted delivery ${resource.spec.name} in application ${resource.spec.application}")
    }
  }

  override fun upsert(resource: Resource<DeliveryConfig>, diff: DiffNode?) {
    runBlocking {
      log.debug("Upserting deliveryconfig $resource")
      val delivery = front50Service
        .upsertDelivery(resource.spec.name, Delivery(id = resource.spec.name, application = resource.spec.application))
        .await()
      log.debug("Upserted delivery $delivery")
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}