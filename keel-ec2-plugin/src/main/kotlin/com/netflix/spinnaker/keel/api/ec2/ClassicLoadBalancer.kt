package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.ec2.cluster.Location
import com.netflix.spinnaker.keel.model.Moniker

data class ClassicLoadBalancer(
  val moniker: Moniker,
  val location: Location,
  val loadBalancerType: LoadBalancerType = LoadBalancerType.CLASSIC,
  val listeners: Set<ClassicLoadBalancerListener> = emptySet(),
  val isInternal: Boolean = true,
  val vpcName: String?,
  val subnetType: String?,
  val securityGroupNames: Set<String> = emptySet(),
  val healthCheck: String, // "$healthCheckProtocol:$healthCheckPort$healthCheckPath",
  val healthInterval: Int = 10,
  val healthyThreshold: Int = 5,
  val unhealthyThreshold: Int = 2,
  val healthTimeout: Int = 5,
  val idleTimeout: Int = 60
)
