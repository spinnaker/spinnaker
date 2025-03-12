package com.netflix.spinnaker.keel.api.plugins

import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.action.ActionState

/**
 * A component responsible for performing verification of an [com.netflix.spinnaker.keel.api.Environment].
 */
interface VerificationEvaluator<VERIFICATION: Verification> {
  val supportedVerification: Pair<String, Class<VERIFICATION>>

  /**
   * @param oldState previous verification state
   * @return updated verification state
   */
  fun evaluate(
    context: ArtifactInEnvironmentContext,
    verification: Verification,
    oldState: ActionState
  ): ActionState

  /**
   * Start running [verification].
   *
   * @return any metadata needed to [evaluate] the verification in future.
   */
  fun start(context: ArtifactInEnvironmentContext, verification: Verification): Map<String, Any?>
}
