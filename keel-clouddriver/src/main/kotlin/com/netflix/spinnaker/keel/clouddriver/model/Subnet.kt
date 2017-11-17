package com.netflix.spinnaker.keel.clouddriver.model

data class Subnet(
  val id: String,
  val vpcId: String,
  val account: String,
  val region: String,
  val availabilityZone: String
)
