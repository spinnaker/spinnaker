package com.netflix.spinnaker.keel.verification

import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.NOT_EVALUATED
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.api.plugins.VerificationEvaluator
import com.netflix.spinnaker.keel.api.verification.VerificationContext
import com.netflix.spinnaker.keel.api.verification.VerificationRepository
import com.netflix.spinnaker.keel.telemetry.VerificationCompleted
import com.netflix.spinnaker.keel.telemetry.VerificationStarted
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class VerificationRunner(
  private val verificationRepository: VerificationRepository,
  private val evaluators: List<VerificationEvaluator<*>>,
  private val eventPublisher: ApplicationEventPublisher
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
        log.debug("Verification already running for {}", environment.name)
        return
      }

      statuses.firstOutstanding?.let { verification ->
        start(verification)
      } ?: log.debug("Verification complete for {}", environment.name)
    }
  }

  private fun VerificationContext.start(verification: Verification) {
    val metadata = evaluators.start(this, verification)
    markAsRunning(verification, metadata)
    eventPublisher.publishEvent(VerificationStarted(this, verification))
  }

  private fun VerificationContext.latestStatus(verification: Verification): ConstraintStatus? {
    val state = previousState(verification)
    return if (state?.status == PENDING) {
      evaluators.evaluate(this, verification, state.metadata)
        .also { newStatus ->
          if (newStatus.complete) {
            log.debug("Verification {} completed with status {} for {}", verification, newStatus, environment.name)
            markAs(verification, newStatus)
            eventPublisher.publishEvent(VerificationCompleted(this, verification, newStatus))
          }
        }
    } else {
      state?.status
    }
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
    metadata: Map<String, Any?>
  ) {
    verificationRepository.updateState(this, verification, PENDING, metadata)
  }

  private fun VerificationContext.markAs(verification: Verification, status: ConstraintStatus) {
    verificationRepository.updateState(this, verification, status)
  }

  private fun List<VerificationEvaluator<*>>.evaluatorFor(verification: Verification) =
    first { it.supportedVerification.first == verification.type }

  private fun List<VerificationEvaluator<*>>.evaluate(
    context: VerificationContext,
    verification: Verification,
    metadata: Map<String, Any?>
  ) =
    evaluatorFor(verification).evaluate(context, verification, metadata)

  private fun List<VerificationEvaluator<*>>.start(context: VerificationContext, verification: Verification) =
    evaluatorFor(verification).start(context, verification)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
