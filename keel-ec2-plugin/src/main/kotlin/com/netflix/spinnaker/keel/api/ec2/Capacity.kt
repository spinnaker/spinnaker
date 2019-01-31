package com.netflix.spinnaker.keel.api.ec2

data class Capacity(
  val min: Int,
  val max: Int,
  val desired: Int
)
