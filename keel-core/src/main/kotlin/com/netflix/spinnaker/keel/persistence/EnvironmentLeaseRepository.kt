package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.exceptions.EnvironmentCurrentlyBeingActedOn
import java.time.Instant

/**
 * A repository that allows you to take a lease (expiring lock) on an environment.
 *
 */
interface EnvironmentLeaseRepository {

  /**
   * Try to take a lease on an environment
   *
   * @param actionType identifies the type of action (e.g., "verification"). Intended just for metric tagging purposes.
   *
   * @returns a lease, if no other client currently holds an active lease on the environment
   *
   * @throws [ActiveLeaseExists] if someone else has a lease on the environment
   */
  fun tryAcquireLease(deliveryConfig: DeliveryConfig, environment: Environment, actionType: String) : Lease
}
