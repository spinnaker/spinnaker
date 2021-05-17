package com.netflix.spinnaker.keel.postdeploy

import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Spectator
import com.netflix.spinnaker.keel.BaseActionRunner
import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.action.ActionRepository
import com.netflix.spinnaker.keel.api.action.ActionState
import com.netflix.spinnaker.keel.api.action.ActionType
import com.netflix.spinnaker.keel.api.action.ActionType.VERIFICATION
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.api.plugins.PostDeployActionHandler
import com.netflix.spinnaker.keel.api.postdeploy.PostDeployAction
import com.netflix.spinnaker.keel.telemetry.PostDeployActionCompleted
import com.netflix.spinnaker.keel.telemetry.PostDeployActionStarted
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * Knows about all the post deploy action handlers and coordinates actually launching actions
 * for each environment
 */
@Component
class PostDeployActionRunner(
  private val handlers: List<PostDeployActionHandler<*>>,
  private val eventPublisher: ApplicationEventPublisher,
  override val actionRepository: ActionRepository,
  override val spectator: Registry
): BaseActionRunner<PostDeployAction>() {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun logSubject() = ActionType.POST_DEPLOY.name

  override fun ArtifactInEnvironmentContext.getActions(): List<PostDeployAction> =
    postDeployActions.toList()

  override fun runInSeries() = false

  override suspend fun evaluate(
    context: ArtifactInEnvironmentContext,
    action: PostDeployAction,
    oldState: ActionState
  ): ActionState =
    handlers.evaluatorFor(action).evaluate(context, action, oldState)


  override fun publishCompleteEvent(context: ArtifactInEnvironmentContext, action: PostDeployAction, state: ActionState) =
    eventPublisher.publishEvent(PostDeployActionCompleted(context, action, state.status, state.metadata))

  override fun publishStartEvent(context: ArtifactInEnvironmentContext, action: PostDeployAction) {
    eventPublisher.publishEvent(PostDeployActionStarted(context, action))
  }

  override fun actionBlocked(context: ArtifactInEnvironmentContext): Boolean =
    if (!actionRepository.allPassed(context, VERIFICATION)) {
      log.debug("Can't run actions for ${context.shortName()}, all verifications are not complete.")
      true
    } else {
      false
    }

  override suspend fun start(context: ArtifactInEnvironmentContext, action: PostDeployAction) {
    val metadata = handlers.start(context, action)
    actionRepository.updateState(context, action, PENDING, metadata)
  }

  private fun List<PostDeployActionHandler<*>>.evaluatorFor(action: PostDeployAction) =
    first { it.supportedType.name == action.type }

  private suspend fun List<PostDeployActionHandler<*>>.start(context: ArtifactInEnvironmentContext, action: PostDeployAction) =
    evaluatorFor(action).start(context, action)
}
