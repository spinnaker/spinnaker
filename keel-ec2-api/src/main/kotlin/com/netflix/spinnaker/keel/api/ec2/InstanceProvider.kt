package com.netflix.spinnaker.keel.api.ec2

/**
 * Extensible mechanism allowing for abstraction of details of the VM instance (e.g.
 * [LaunchConfigurationSpec.instanceType], [LaunchConfigurationSpec.ebsOptimized], etc.)
 */
interface InstanceProvider
