package com.netflix.spinnaker.keel.verification

import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.NOT_EVALUATED
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.api.plugins.CurrentImages
import com.netflix.spinnaker.keel.api.plugins.VerificationEvaluator
import com.netflix.spinnaker.keel.api.verification.VerificationContext
import com.netflix.spinnaker.keel.api.verification.VerificationRepository
import com.netflix.spinnaker.keel.api.verification.VerificationState
import com.netflix.spinnaker.keel.enforcers.EnvironmentExclusionEnforcer
import com.netflix.spinnaker.keel.telemetry.VerificationCompleted
import com.netflix.spinnaker.keel.telemetry.VerificationStarted
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class VerificationRunner(
  private val verificationRepository: VerificationRepository,
  private val evaluators: List<VerificationEvaluator<*>>,
  private val eventPublisher: ApplicationEventPublisher,
  private val imageFinder: ImageFinder,
  private val enforcer: EnvironmentExclusionEnforcer,
) {
  /**
   * Evaluates the state of any currently running verifications and launches the next, against a
   * particular environment and artifact version.
   */
  fun runVerificationsFor(context: VerificationContext) {
    with(context) {
      val statuses = environment
        .verifyWith
        .map { verification ->
          verification to latestStatus(verification)
        }

      if (statuses.anyStillRunning) {
        log.debug("Verification already running for environment {} of application {}", environment.name, deliveryConfig.application)
        return
      }

      statuses.firstOutstanding?.let { verification ->
        enforcer.withVerificationLease(this) {
          start(verification, imageFinder.getImages(context.deliveryConfig, context.environmentName))
        }
      } ?: log.debug("Verification complete for environment {} of application {}", environment.name, deliveryConfig.application)
    }
  }

  private fun VerificationContext.start(verification: Verification, images: List<CurrentImages>) {
    val metadata = evaluators.start(this, verification) + mapOf("images" to images)
    markAsRunning(verification, metadata)
    eventPublisher.publishEvent(VerificationStarted(this, verification))
  }

  private fun VerificationContext.latestStatus(verification: Verification): ConstraintStatus? {
    val oldState = previousState(verification)
    val newState = if (oldState?.status == PENDING) {
      evaluators.evaluate(this, verification, oldState)
        .also { newState ->
          val newStatus = newState.status
          if (newStatus.complete) {
            log.debug("Verification {} completed with status {} for environment {} of application {}",
              verification, newStatus, environment.name, deliveryConfig.application)
            markAs(verification, newStatus, newState.link)
            eventPublisher.publishEvent(VerificationCompleted(this, verification, newStatus, newState.metadata))
          }
        }
    } else {
      oldState
    }
    return newState?.status
  }

  private val Collection<Pair<*, ConstraintStatus?>>.anyStillRunning: Boolean
    get() = any { (_, status) -> status == PENDING }

  private val Collection<Pair<Verification, ConstraintStatus?>>.firstOutstanding: Verification?
    get() = firstOrNull { (_, status) -> status in listOf(null, NOT_EVALUATED) }?.first

  private fun VerificationContext.previousState(verification: Verification) =
    verificationRepository
      .getState(this, verification)

  private fun VerificationContext.markAsRunning(
    verification: Verification,
    metadata: Map<String, Any?>,
    link: String? = null
  ) {
    verificationRepository.updateState(this, verification, PENDING, metadata, link)
  }

  private fun VerificationContext.markAs(verification: Verification, status: ConstraintStatus, link: String?) {
    verificationRepository.updateState(this, verification, status, link=link)
  }

  private fun List<VerificationEvaluator<*>>.evaluatorFor(verification: Verification) =
    first { it.supportedVerification.first == verification.type }

  private fun List<VerificationEvaluator<*>>.evaluate(
    context: VerificationContext,
    verification: Verification,
    oldState: VerificationState
  ) =
    evaluatorFor(verification).evaluate(context, verification, oldState)

  private fun List<VerificationEvaluator<*>>.start(context: VerificationContext, verification: Verification) =
    evaluatorFor(verification).start(context, verification)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
