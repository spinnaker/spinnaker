package com.netflix.spinnaker.keel.api.deliveryconfig

data class DeliveryConfig(
  val name: String,
  val application: String,
  val deliveryArtifacts: List<Map<String, Any>> = emptyList(),
  val deliveryEnvironments: List<DeliveryEnvironment> = emptyList()
)
