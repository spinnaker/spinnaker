package com.netflix.spinnaker.keel.api.plugins

import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.verification.VerificationContext

/**
 * A component responsible for performing verification of an [com.netflix.spinnaker.keel.api.Environment].
 */
interface VerificationEvaluator<VERIFICATION: Verification> {
  val supportedVerification: Pair<String, Class<VERIFICATION>>

  /**
   * @param metadata as returned by [start].
   * @return the current status of the verification.
   */
  fun evaluate(
    context: VerificationContext,
    verification: Verification,
    metadata: Map<String, Any?>
  ): ConstraintStatus

  /**
   * Start running [verification].
   *
   * @return any metadata needed to [evaluate] the verification in future.
   */
  fun start(context: VerificationContext, verification: Verification): Map<String, Any?>
}
