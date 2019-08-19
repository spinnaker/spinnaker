package com.netflix.spinnaker.keel.api.ec2

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.keel.api.ec2.cluster.Location
import com.netflix.spinnaker.keel.model.Moniker

data class ClassicLoadBalancer(
  override val moniker: Moniker,
  override val location: Location,
  override val loadBalancerType: LoadBalancerType = LoadBalancerType.CLASSIC,
  override val internal: Boolean = true,
  override val vpcName: String?,
  override val subnetType: String?,
  override val securityGroupNames: Set<String> = emptySet(),
  override val idleTimeout: Int = 60,
  val listeners: Set<ClassicLoadBalancerListener> = emptySet(),
  val healthCheck: String, // "$healthCheckProtocol:$healthCheckPort$healthCheckPath",
  val healthInterval: Int = 10,
  val healthyThreshold: Int = 5,
  val unhealthyThreshold: Int = 2,
  val healthTimeout: Int = 5
) : LoadBalancer {
  init {
    require(moniker.name.length <= 32) {
      "load balancer names have a 32 character limit"
    }
  }

  @JsonIgnore
  override val name: String = "${location.accountName}:${location.region}:${moniker.name}"
}
