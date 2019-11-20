package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.ConstraintState
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.ResourceId

interface DeliveryConfigRepository : PeriodicallyCheckedRepository<DeliveryConfig> {

  fun store(deliveryConfig: DeliveryConfig)

  fun get(name: String): DeliveryConfig

  fun environmentFor(resourceId: ResourceId): Environment?

  fun deliveryConfigFor(resourceId: ResourceId): DeliveryConfig?

  fun deleteByApplication(application: String): Int

  fun storeConstraintState(state: ConstraintState)

  fun getConstraintState(
    deliveryConfigName: String,
    environmentName: String,
    artifactVersion: String,
    type: String
  ): ConstraintState?

  fun constraintStateFor(
    deliveryConfigName: String,
    environmentName: String,
    limit: Int = 10
  ): List<ConstraintState>
}

sealed class NoSuchDeliveryConfigException(message: String) : RuntimeException(message)
class NoSuchDeliveryConfigName(name: String) : NoSuchDeliveryConfigException("No delivery config named $name exists in the repository")
