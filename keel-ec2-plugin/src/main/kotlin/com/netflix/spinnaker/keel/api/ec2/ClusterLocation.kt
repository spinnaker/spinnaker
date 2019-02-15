package com.netflix.spinnaker.keel.api.ec2

data class ClusterLocation(
  val accountName: String,
  val region: String,
  val subnet: String?, // TODO: is this actually optional?
  val availabilityZones: Set<String>
)
