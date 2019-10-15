package com.netflix.spinnaker.keel.api.ec2

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerType.CLASSIC
import com.netflix.spinnaker.keel.model.Moniker
import java.time.Duration

data class ClassicLoadBalancerSpec(
  override val moniker: Moniker,
  override val locations: SubnetAwareLocations,
  override val internal: Boolean = true,
  override val dependencies: LoadBalancerDependencies = LoadBalancerDependencies(),
  val listeners: Set<ClassicLoadBalancerListener> = emptySet(),
  val healthCheck: ClassicLoadBalancerHealthCheck,
  override val idleTimeout: Duration = Duration.ofSeconds(60)
) : LoadBalancerSpec {
  init {
    require(moniker.name.length <= 32) {
      "load balancer names have a 32 character limit"
    }
  }

  @JsonIgnore
  override val loadBalancerType: LoadBalancerType = CLASSIC

  @JsonIgnore
  override val id: String = "${locations.account}:${moniker.name}"
}
