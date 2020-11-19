package com.netflix.spinnaker.keel.verification

import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.plugins.VerificationEvaluator
import com.netflix.spinnaker.keel.api.verification.VerificationContext
import com.netflix.spinnaker.keel.api.verification.VerificationRepository
import com.netflix.spinnaker.keel.api.verification.VerificationStatus
import com.netflix.spinnaker.keel.api.verification.VerificationStatus.RUNNING
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
    evaluators.start(verification)
    markAsRunning(verification)
    eventPublisher.publishEvent(VerificationStarted(this, verification))
  }

  private fun VerificationContext.latestStatus(verification: Verification): VerificationStatus? {
    val status = previousStatus(verification)
    return if (status == RUNNING) {
      evaluators.evaluate(verification)
        .also { newStatus ->
          if (newStatus.complete) {
            log.debug("Verification {} completed with status {} for {}", verification, newStatus, environment.name)
            markAs(verification, newStatus)
            eventPublisher.publishEvent(VerificationCompleted(this, verification, newStatus))
          }
        }
    } else {
      status
    }
  }

  private val Collection<Pair<*, VerificationStatus?>>.anyStillRunning: Boolean
    get() = any { (_, status) -> status == RUNNING }

  private val Collection<Pair<Verification, VerificationStatus?>>.firstOutstanding: Verification?
    get() = firstOrNull { (_, status) -> status == null }?.first

  private fun VerificationContext.previousStatus(verification: Verification) =
    verificationRepository
      .getState(this, verification)
      ?.status

  private fun VerificationContext.markAsRunning(verification: Verification) {
    markAs(verification, RUNNING)
  }

  private fun VerificationContext.markAs(verification: Verification, status: VerificationStatus) {
    verificationRepository.updateState(this, verification, status)
  }

  private fun List<VerificationEvaluator<*>>.evaluatorFor(verification: Verification) =
    first { it.supportedVerification.first == verification.type }

  private fun List<VerificationEvaluator<*>>.evaluate(verification: Verification) =
    evaluatorFor(verification).evaluate()

  private fun List<VerificationEvaluator<*>>.start(verification: Verification) =
    evaluatorFor(verification).start(verification)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
