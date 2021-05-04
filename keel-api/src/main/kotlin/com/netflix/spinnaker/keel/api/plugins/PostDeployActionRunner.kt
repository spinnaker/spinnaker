package com.netflix.spinnaker.keel.api.plugins

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
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
interface PostDeployActionRunner<T: PostDeployAction> : SpinnakerExtensionPoint {
  /**
   * The supported post deploy action type mapping for this runner.
   */
  val supportedType: SupportedPostDeployActionType<T>
  val eventPublisher: EventPublisher

  //todo eb: retry config?

  //todo eb: centralized eventing for monitoring?

  /**
   * Launches the post deploy action
   */
  fun launch(
    artifact: DeliveryArtifact,
    artifactVersion: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment
  )
}
