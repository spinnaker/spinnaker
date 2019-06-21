package com.netflix.spinnaker.keel.api.deliveryconfig

import com.netflix.spinnaker.keel.api.ApiVersion

data class DeliveryEnvironment(
  val name: String,
  val packageRef: ChildResource,
  val targets: List<ChildResource>
)

data class ChildResource(
  val apiVersion: ApiVersion,
  val kind: String,
  val spec: Map<String, Any?>,
  val metadata: Map<String, Any?>?
)
