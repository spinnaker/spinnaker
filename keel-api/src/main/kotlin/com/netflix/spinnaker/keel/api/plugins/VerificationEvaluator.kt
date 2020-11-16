package com.netflix.spinnaker.keel.api.plugins

import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.verification.VerificationStatus

/**
 * A component responsible for performing verification of an [com.netflix.spinnaker.keel.api.Environment].
 */
interface VerificationEvaluator<VERIFICATION: Verification> {
  val supportedVerification: Pair<String, Class<VERIFICATION>>

  /**
   * @return the current status of the verification.
   */
  fun evaluate() : VerificationStatus

  /**
   * Start running [verification].
   */
  fun start(verification: Verification)
}
