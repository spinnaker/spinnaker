package com.netflix.spinnaker.keel.enforcers

import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.histogram.PercentileTimer
import org.springframework.core.env.Environment as SpringEnvironment
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.verification.VerificationContext
import org.springframework.stereotype.Component

/**
 * Exception thrown when it's not safe to take action against the environment because
 * something is already acting on it.
 */
class EnvironmentCurrentlyBeingActedOn(message: String) : Exception(message) { }

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
  spectator: Registry
) {

  private val enforcementEnabled: Boolean
    get() = springEnv.getProperty("keel.enforcement.environment-exclusion.enabled", Boolean::class.java, true)


  /**
   * Percentile timer for measuring how long the checks take.
   *
   * We use the default percentile time range: 10ms to 1 minute
   */
  private val timerBuilder = PercentileTimer.builder(spectator).withName("keel.enforcement.environment.check.duration")


  /**
   * To get a verification lease against an environment, need:
   *
   * 1. An environment lease
   * 2. No active deployments
   * 3. No active verifications
   */
  fun <T> withVerificationLease(context: VerificationContext, action: () -> T) : T {
      recordDuration("verification") {
        if (enforcementEnabled) {
          val environment = context.environment

          ensureNoActiveDeployments(environment)
          ensureNoActiveVerifications(environment)
        }
      }
    return action.invoke()
  }

  /**
   * To get an actuation lease against an environment, need:
   *
   * 1. An environment lease
   * 2. No active verifications
   *
   * It's ok if other actuations (e.g., deployments) are going on.
   */
  suspend fun <T> withActuationLease(environment: Environment, action: suspend () -> T) : T {
    recordDuration("actuation") {
      if (enforcementEnabled) {
        ensureNoActiveVerifications(environment)
      }
    }
    return action.invoke()
  }


  /**
   * @throws EnvironmentCurrentlyBeingActedOn if there's an active deployment
   */
  private fun ensureNoActiveDeployments(environment: Environment) {
    /**
     * To be implemented in a future PR.
     */
  }

  /**
   * @throws EnvironmentCurrentlyBeingActedOn if there's an active verification
   */
  private fun ensureNoActiveVerifications(environment: Environment) {
    /**
     * To be implemented in a future PR
     */
  }

  /**
   * Emit a metric with the duration time for the given block of code
   */
  private fun recordDuration(actionType: String, block: () -> Unit) {
    timerBuilder
      .withTag("action", actionType)
      .build()
      .record {
        block.invoke()
      }
  }
}
