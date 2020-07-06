package com.netflix.spinnaker.keel.api.ec2

data class LaunchConfigurationSpec(
  val image: VirtualMachineImage? = null,
  // TODO: name sucks
  val instanceProvider: InstanceProvider? = null,
  val instanceType: String? = null,
  val ebsOptimized: Boolean? = null,
  val iamRole: String? = null,
  val keyPair: String? = null,
  val instanceMonitoring: Boolean? = null,
  val ramdiskId: String? = null
)
