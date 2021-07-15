package com.netflix.spinnaker.keel.api.plugins

import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.action.ActionState
import com.netflix.spinnaker.keel.api.postdeploy.PostDeployAction
import com.netflix.spinnaker.keel.api.postdeploy.SupportedPostDeployActionType
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint

/**
 * A post deploy action is an action that runs after a change
 * to an environment has been deployed (and if defined, verified).
 *
 * All post deploy actions are run in parallel.
 */
interface PostDeployActionHandler<T: PostDeployAction> : SpinnakerExtensionPoint {
  /**
   * The supported post deploy action type mapping for this runner.
   */
  val supportedType: SupportedPostDeployActionType<T>
  val eventPublisher: EventPublisher

  //todo eb: retry config?

  //todo eb: centralized eventing for monitoring?

  /**
   * Start running [action]
   *
   * @return any metadata needed to evaluate the action in the future
   */
  suspend fun start(
    context: ArtifactInEnvironmentContext,
    action: PostDeployAction
  ): Map<String, Any?>

  /**
   * @param oldState previous action state
   * @return updated action state
   */
  suspend fun evaluate(
    context: ArtifactInEnvironmentContext,
    action: PostDeployAction,
    oldState: ActionState
  ): ActionState
}
