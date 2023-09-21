package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.Dependency
import com.netflix.spinnaker.keel.api.DependencyType.SECURITY_GROUP
import com.netflix.spinnaker.keel.api.Dependent
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
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
) : LoadBalancerSpec, Dependent {

  init {
    require(moniker.toString().length <= 32) {
      "load balancer names have a 32 character limit"
    }
  }

  override val loadBalancerType: LoadBalancerType = CLASSIC

  override val id: String = "${locations.account}:$moniker"

  override val dependsOn: Set<Dependency>
    get() = locations.regions.flatMap { region ->
      dependencies.securityGroupNames.map { Dependency(SECURITY_GROUP, region.name, it) }
    }.toSet() +
      overrides.flatMap { (region, override) ->
        override.dependencies?.securityGroupNames?.map { Dependency(SECURITY_GROUP, region, it) } ?: emptySet()
      }

  override fun deepRename(suffix: String): ClassicLoadBalancerSpec =
    copy(moniker = moniker.withSuffix(suffix))
}

data class ClassicLoadBalancerOverride(
  val dependencies: LoadBalancerDependencies? = null,
  val listeners: Set<ClassicLoadBalancerListener>? = null,
  val healthCheck: ClassicLoadBalancerHealthCheck? = null
)
