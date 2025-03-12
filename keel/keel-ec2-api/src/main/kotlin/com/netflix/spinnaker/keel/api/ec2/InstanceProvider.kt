package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.schema.Discriminator

/**
 * Extensible mechanism allowing for abstraction of details of the VM instance (e.g.
 * [LaunchConfigurationSpec.instanceType], [LaunchConfigurationSpec.ebsOptimized], etc.)
 */
interface InstanceProvider {
  @Discriminator
  val type: String
}
