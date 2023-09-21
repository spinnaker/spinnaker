package com.netflix.spinnaker.keel.verification

import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Spectator
import com.netflix.spinnaker.keel.BaseActionRunner
import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.NOT_EVALUATED
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.api.plugins.CurrentImages
import com.netflix.spinnaker.keel.api.plugins.VerificationEvaluator
import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.action.ActionRepository
import com.netflix.spinnaker.keel.api.action.ActionState
import com.netflix.spinnaker.keel.api.action.ActionType
import com.netflix.spinnaker.keel.enforcers.EnvironmentExclusionEnforcer
import com.netflix.spinnaker.keel.telemetry.VerificationCompleted
import com.netflix.spinnaker.keel.telemetry.VerificationStarted
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class VerificationRunner(
  override val actionRepository: ActionRepository,
  private val evaluators: List<VerificationEvaluator<*>>,
  private val eventPublisher: ApplicationEventPublisher,
  private val imageFinder: ImageFinder,
  private val enforcer: EnvironmentExclusionEnforcer,
  override val spectator: Registry
): BaseActionRunner<Verification>() {
  override fun logSubject() = ActionType.VERIFICATION.name

  override fun ArtifactInEnvironmentContext.getActions(): List<Verification> =
    environment.verifyWith

  /**
   * Verifications are run one at a time
   */
  override fun runInSeries() = true

  override fun actionBlocked(context: ArtifactInEnvironmentContext): Boolean {
    return false
  }

  override suspend fun start(context: ArtifactInEnvironmentContext, action: Verification) {
    enforcer.withVerificationLease(context) {
      log.debug("Starting verification for ${context.shortName()}")
      val images = imageFinder.getImages(context.deliveryConfig, context.environmentName)
      val metadata = evaluators.start(context, action) + mapOf("images" to images)
      actionRepository.updateState(context, action, PENDING, metadata)
    }
  }

  override suspend fun evaluate(
    context: ArtifactInEnvironmentContext,
    action: Verification,
    oldState: ActionState
  ): ActionState =
    evaluators.evaluatorFor(action).evaluate(context, action, oldState)

  override fun publishCompleteEvent(context: ArtifactInEnvironmentContext, action: Verification, state: ActionState) {
    eventPublisher.publishEvent(VerificationCompleted(context, action, state.status, state.metadata))
  }

  override fun publishStartEvent(context: ArtifactInEnvironmentContext, action: Verification) {
    eventPublisher.publishEvent(VerificationStarted(context, action))
  }

  private fun List<VerificationEvaluator<*>>.evaluatorFor(verification: Verification) =
    first { it.supportedVerification.first == verification.type }

  private fun List<VerificationEvaluator<*>>.start(context: ArtifactInEnvironmentContext, verification: Verification) =
    evaluatorFor(verification).start(context, verification)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
