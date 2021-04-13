package com.netflix.spinnaker.keel.titus.verification

/**
 * Strategy for generating a Titus UI link using the value of the "jobStatus" variable in Orca's execution detail
 * response for running a Titus job
 */
interface LinkStrategy {
  fun url(jobStatus: Map<String, Any?>) : String?
}
