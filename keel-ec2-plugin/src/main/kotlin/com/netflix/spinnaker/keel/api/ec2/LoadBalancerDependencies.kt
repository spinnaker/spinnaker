package com.netflix.spinnaker.keel.api.ec2

data class LoadBalancerDependencies(
  val securityGroupNames: Set<String> = emptySet()
)
