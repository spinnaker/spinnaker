package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerType.CLASSIC
import java.time.Duration

data class ClassicLoadBalancer(
  val moniker: Moniker,
  val location: Location,
  val internal: Boolean = true,
  val dependencies: LoadBalancerDependencies = LoadBalancerDependencies(),
  val listeners: Set<ClassicLoadBalancerListener> = emptySet(),
  val healthCheck: ClassicLoadBalancerHealthCheck,
  val idleTimeout: Duration = Duration.ofSeconds(60)
) {
  val loadBalancerType: LoadBalancerType = CLASSIC
}
