package com.netflix.spinnaker.keel.api.action

import com.netflix.spinnaker.keel.api.schema.Discriminator

/**
 * Base interface for types of actions.
 */
interface Action {
  @Discriminator
  val type: String

  /**
   * Identifier used to distinguish between different instances.
   */
  val id: String

  val actionType: ActionType
}
