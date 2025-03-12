package com.netflix.spinnaker.keel.bakery.artifact

import com.netflix.spinnaker.keel.api.artifacts.BaseLabel

data class BakeStage(
  val type: String,
  val outputs: Outputs
)

data class Outputs(
  val deploymentDetails: List<DeploymentDetail>
)

data class DeploymentDetail(
  val ami: String,
  val imageId: String,
  val imageName: String,
  val baseLabel: BaseLabel,
  val baseOs: String,
  val storeType: String,
  val vmType: String,
  val region: String,
  val `package`: String,
  val cloudProviderType: String,
  val baseAmiId: String
)
