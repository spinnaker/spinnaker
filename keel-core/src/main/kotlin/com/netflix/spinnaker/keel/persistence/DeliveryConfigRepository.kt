package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.ResourceId

interface DeliveryConfigRepository : PeriodicallyCheckedRepository<DeliveryConfig> {

  fun store(deliveryConfig: DeliveryConfig)

  fun get(name: String): DeliveryConfig

  fun environmentFor(resourceId: ResourceId): Environment?

  fun deliveryConfigFor(resourceId: ResourceId): DeliveryConfig?
}

sealed class NoSuchDeliveryConfigException(message: String) : RuntimeException(message)
class NoSuchDeliveryConfigName(name: String) : NoSuchDeliveryConfigException("No delivery config named $name exists in the repository")
