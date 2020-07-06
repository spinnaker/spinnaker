package com.netflix.spinnaker.keel.api.ec2

data class VirtualMachineImage(
  val id: String,
  val appVersion: String,
  val baseImageVersion: String
)
