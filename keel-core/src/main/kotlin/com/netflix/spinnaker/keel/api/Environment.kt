package com.netflix.spinnaker.keel.api

data class Environment(
  val name: String,
  val artifacts: Set<DeliveryArtifact>,
  val resources: Set<Resource<*>>
)
