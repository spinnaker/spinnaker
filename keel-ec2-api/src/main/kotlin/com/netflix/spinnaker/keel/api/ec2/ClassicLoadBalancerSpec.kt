package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.UnhappyControl
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerType.CLASSIC
import com.netflix.spinnaker.keel.api.schema.Optional
import java.time.Duration

data class ClassicLoadBalancerSpec(
  override val moniker: Moniker,
  @Optional override val locations: SubnetAwareLocations,
  override val internal: Boolean = true,
  override val dependencies: LoadBalancerDependencies = LoadBalancerDependencies(),
  override val idleTimeout: Duration = Duration.ofSeconds(60),
  val listeners: Set<ClassicLoadBalancerListener> = emptySet(),
  val healthCheck: ClassicLoadBalancerHealthCheck,
  val overrides: Map<String, ClassicLoadBalancerOverride> = emptyMap()
) : LoadBalancerSpec, UnhappyControl {

  init {
    require(moniker.toString().length <= 32) {
      "load balancer names have a 32 character limit"
    }
  }

  override val maxDiffCount: Int? = 2

  // Once load balancers go unhappy, only retry when the diff changes, or if manually unvetoed
  override val unhappyWaitTime: Duration? = null

  override val loadBalancerType: LoadBalancerType = CLASSIC

  override val id: String = "${locations.account}:$moniker"
}

data class ClassicLoadBalancerOverride(
  val dependencies: LoadBalancerDependencies? = null,
  val listeners: Set<ClassicLoadBalancerListener>? = null,
  val healthCheck: ClassicLoadBalancerHealthCheck? = null
)
