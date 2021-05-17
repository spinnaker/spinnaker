package com.netflix.spinnaker.keel.enforcers

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.action.ActionRepository
import com.netflix.spinnaker.keel.exceptions.EnvironmentCurrentlyBeingActedOn
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.EnvironmentLeaseRepository
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import org.springframework.core.env.Environment as SpringEnvironment

class ActiveVerifications(val active: Collection<ArtifactInEnvironmentContext>, deliveryConfig: DeliveryConfig, environment: Environment) :
  EnvironmentCurrentlyBeingActedOn("active verifications in ${deliveryConfig.name} ${environment.name} against versions ${active.map {it.version}}")

class ActiveDeployments(deliveryConfig: DeliveryConfig, environment: Environment ) :
  EnvironmentCurrentlyBeingActedOn("currently deploying into ${deliveryConfig.name} ${environment.name}")

/**
 * This class enforces two safety properties of the verification behavior:
 *
 * P1: Two verifications should never execute concurrently against the same environment.
 *
 *   For example, if the acme/tests:stable test container is currently running in the staging environment,
 *   keel should not launch any other verifications in the staging environment.
 *
 * P2:  Verifications against an environment should never happen concurrently with a deployment in an environment.
 *
 *   For example, if the fnord-v123.deb artifact is being deployed to an EC2 cluster in the staging environment,
 *   keel should not launch verifications while this is happening.
 *
 *
 * The enforcer works by granting the client a lease if the guard conditions are met.
 * A lease is a lock that has an expiration time. Leases expire to protect against the situation
 * where an instance takes a lease and then terminates unexpectedly before releasing it.
 *
 * If a client is unable to get a lease, the enforcer will throw a EnvironmentCurrentlyBeingActedOn exception.
 *
 */
@Component
class EnvironmentExclusionEnforcer(
  private val springEnv: SpringEnvironment,
  private val verificationRepository: ActionRepository,
  private val artifactRepository: ArtifactRepository,
  private val environmentLeaseRepository: EnvironmentLeaseRepository
) {

  private val enforcementEnabled: Boolean
    get() = springEnv.getProperty("keel.enforcement.environment-exclusion.enabled", Boolean::class.java, true)

  /**
   * To get a verification lease against an environment, need:
   *
   * 1. An environment lease
   * 2. No active deployments
   * 3. No active verifications
   */
  fun <T> withVerificationLease(context: ArtifactInEnvironmentContext, action: () -> T) : T {
    if(!enforcementEnabled) {
      return action.invoke()
    }

    with(context) {
      environmentLeaseRepository.tryAcquireLease(deliveryConfig, environment, "verification").use {

        // These will throw exceptions if the checks fail
        ensureNoActiveDeployments(deliveryConfig, environment)
        ensureNoActiveVerifications(deliveryConfig, environment)

        // it's now safe to do the action
        return action.invoke()
      }
    }
  }

  /**
   * To get an actuation lease against an environment, need:
   *
   * 1. An environment lease
   * 2. No active verifications
   *
   * It's ok if other actuations (e.g., deployments) are going on.
   */
  suspend fun <T> withActuationLease(deliveryConfig: DeliveryConfig, environment: Environment, action: suspend () -> T) : T {
    if(!enforcementEnabled) {
      return action.invoke()
    }

    // use IO context since the checks call the database, which will block the coroutine's thread
    return withContext(IO) {
      environmentLeaseRepository.tryAcquireLease(deliveryConfig, environment, "actuation").use {

        // This will throw an exception if the check fails
        ensureNoActiveVerifications(deliveryConfig, environment)

        // it's now safe to do the action
        return@withContext action.invoke()
      }
    }
  }

  /**
   * @throws ActiveDeployments if there's an active deployment in [environment]
   */
  private fun ensureNoActiveDeployments(deliveryConfig: DeliveryConfig, environment: Environment) {
    if(artifactRepository.isDeployingTo(deliveryConfig, environment.name)) {
      throw ActiveDeployments(deliveryConfig, environment)
    }
  }

  /**
   *
   * Checks if any verifications in the [environment] of [deliveryConfig] are in the [PENDING] state
   *
   * @throws ActiveVerifications if there's an active verification
   */
  private fun ensureNoActiveVerifications(deliveryConfig: DeliveryConfig, environment: Environment)  {
    val activeVerifications = verificationRepository.getVerificationContextsWithStatus(deliveryConfig, environment, PENDING)
    if(activeVerifications.isNotEmpty()) {
      throw ActiveVerifications(activeVerifications, deliveryConfig, environment)
    }
  }
}
