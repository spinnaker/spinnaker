package com.netflix.spinnaker.keel.titus.verification

import com.netflix.spinnaker.keel.orca.ExecutionDetailResponse

/**
 * Determine the UI link to show the user about the status of the verification
 */
fun getLink(response: ExecutionDetailResponse, linkStrategy: LinkStrategy?) : String? =
  response.getJobStatus()?.let { linkStrategy?.url(it) }

/**
 * Retrieve the value of the "jobStatus" variable from an Orca task response
 */
@Suppress("UNCHECKED_CAST")
fun ExecutionDetailResponse.getJobStatus() : Map<String, Any?>? =
  variables?.firstOrNull { it.key == JOB_STATUS }?.value as Map<String, Any?>?

