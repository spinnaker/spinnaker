package com.netflix.spinnaker.keel.verification

import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.NOT_EVALUATED
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.api.plugins.CurrentImages
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
  private val eventPublisher: ApplicationEventPublisher,
  private val imageFinder: ImageFinder
) {
  /**
   * Evaluates the state of any currently running verifications and launches the next, against a
   * particular environment and artifact version.
   */
  fun runVerificationsFor(context: VerificationContext) {
    if (context.environment.verifyWith.isEmpty()) {
      return
    }

    verificationRepository
      .pendingInEnvironment(context.deliveryConfig, context.environmentName)
      // only consider other versions, we'll handle verifications for the version in context later
      .filterNot { it.context.version == context.version }
      // get the latest status by re-evaluating each one (which will update in the database)
      .associateWith { it.context.latestStatus(it.verification) }
      // filter out things that have now completed (since we last checked)
      .filterNot { (_,  status) ->
        status?.complete ?: false
      }
      .let { pendingVerifications ->
        // if we still have any pending verifications then something is still running for a previous
        // version of the artifact -- we should wait
        if (pendingVerifications.isNotEmpty()) {
          pendingVerifications.forEach { (pendingVerification, status)->
            log.debug("Previous verification {} for {} is still {}", pendingVerification.verification.id, pendingVerification.context.version, status)
          }
          return
        }
      }

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
        start(verification, imageFinder.getImages(context.deliveryConfig, context.environmentName))
      } ?: log.debug("Verification complete for environment {} of application {}", environment.name, deliveryConfig.application)
    }
  }

  private fun VerificationContext.start(verification: Verification, images: List<CurrentImages>) {
    val metadata = evaluators.start(this, verification) + mapOf("images" to images)
    markAsRunning(verification, metadata)
    eventPublisher.publishEvent(VerificationStarted(this, verification))
  }

  private fun VerificationContext.latestStatus(verification: Verification): ConstraintStatus? {
    val state = previousState(verification)
    return if (state?.status == PENDING) {
      evaluators.evaluate(this, verification, state.metadata)
        .also { newStatus ->
          if (newStatus.complete) {
            log.debug("Verification {} completed with status {} for environment {} of application {}",
              verification, newStatus, environment.name, deliveryConfig.application)
            markAs(verification, newStatus)
            eventPublisher.publishEvent(VerificationCompleted(this, verification, newStatus, state.metadata))
          }
        }
    } else {
      state?.status
    }
  }

  /**
   * `true` if any of the statuses is [PENDING], `false` if none are or the collection is empty.
   */
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
