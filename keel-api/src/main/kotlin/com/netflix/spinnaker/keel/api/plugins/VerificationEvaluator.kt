package com.netflix.spinnaker.keel.api.plugins

import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.verification.VerificationContext
import com.netflix.spinnaker.keel.api.verification.VerificationState

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
    context: VerificationContext,
    verification: Verification,
    oldState: VerificationState
  ): VerificationState

  /**
   * Start running [verification].
   *
   * @return any metadata needed to [evaluate] the verification in future.
   */
  fun start(context: VerificationContext, verification: Verification): Map<String, Any?>
}
