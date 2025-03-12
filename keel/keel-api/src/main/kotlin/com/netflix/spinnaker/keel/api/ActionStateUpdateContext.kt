package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.action.ActionType


/**
 * This class encapsulates the information needed to update state information for an action, for all artifact versions
 */
data class ActionStateUpdateContext(
  val deliveryConfig: DeliveryConfig,
  val environment: Environment,
  val actionType: ActionType,
  val id: String
)
