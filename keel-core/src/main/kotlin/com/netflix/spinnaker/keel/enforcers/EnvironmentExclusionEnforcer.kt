package com.netflix.spinnaker.keel.enforcers

import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.histogram.PercentileTimer
import com.netflix.spinnaker.keel.api.DeliveryConfig
import org.springframework.core.env.Environment as SpringEnvironment
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.api.verification.VerificationContext
import com.netflix.spinnaker.keel.api.verification.VerificationRepository
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Exception thrown when it's not safe to take action against the environment because
 * something is already acting on it.
 */
open class EnvironmentCurrentlyBeingActedOn(message: String) : Exception(message) { }

class ActiveVerifications(val active: Collection<VerificationContext>, deliveryConfig: DeliveryConfig, environment: Environment) :
  EnvironmentCurrentlyBeingActedOn("active verifications in ${deliveryConfig.name} ${environment.name} against versions ${active.map {it.version}}")

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
  private val repository: VerificationRepository,
  spectator: Registry,
  private val clock: Clock
  ) {

  private val enforcementEnabled: Boolean
    get() = springEnv.getProperty("keel.enforcement.environment-exclusion.enabled", Boolean::class.java, true)


  /**
   * Percentile timer builder for measuring how long the checks take.
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
    val startTime = clock.instant()

    if (enforcementEnabled) {
      val environment = context.environment
      val deliveryConfig = context.deliveryConfig

      ensureNoActiveDeployments(deliveryConfig, environment)
      ensureNoActiveVerifications(deliveryConfig, environment)
    }

    recordDuration("verification", startTime)
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
  suspend fun <T> withActuationLease(deliveryConfig: DeliveryConfig, environment: Environment, action: suspend () -> T) : T {
    val startTime = clock.instant()

    if (enforcementEnabled) {
      // use IO context since the checks call the database, which wil block the coroutine's thread
      withContext(IO) {
        ensureNoActiveVerifications(deliveryConfig, environment)
      }
    }

    recordDuration("actuation", startTime)
    return action.invoke()
  }

  /**
   * @throws EnvironmentCurrentlyBeingActedOn if there's an active deployment
   */
  private fun ensureNoActiveDeployments(deliveryConfig: DeliveryConfig, environment: Environment) {
    /**
     * To be implemented in a future PR.
     */
  }

  /**
   *
   * Checks if any verifications in the [environment] of [deliveryConfig] are in the [PENDING] state
   *
   * @throws ActiveVerifications if there's an active verification
   */
  private fun ensureNoActiveVerifications(deliveryConfig: DeliveryConfig, environment: Environment)  {
    val activeVerifications = repository.getContextsWithStatus(deliveryConfig, environment, PENDING)
    if(activeVerifications.isNotEmpty()) {
      throw ActiveVerifications(activeVerifications, deliveryConfig, environment)
    }
  }

  /**
   * Emit a metric with the duration of the check time from [startTime] to now.
   *
   * Tag with key: "action", value: [actionType]
   */
  private fun recordDuration(actionType: String, startTime: Instant) {
    timerBuilder
      .withTag("action", actionType)
      .build()
      .record(Duration.between(startTime, clock.instant()))
  }
}
