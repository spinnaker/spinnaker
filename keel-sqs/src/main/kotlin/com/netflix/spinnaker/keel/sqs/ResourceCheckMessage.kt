package com.netflix.spinnaker.keel.sqs

import com.netflix.spinnaker.keel.api.ApiVersion

internal data class ResourceCheckMessage(
  val name: String,
  val apiVersion: ApiVersion,
  val kind: String
)
