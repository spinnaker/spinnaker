package com.netflix.spinnaker.keel.api.ec2.old

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.Action
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.Rule
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.TargetGroup
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerDependencies
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerType
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerType.APPLICATION
import com.netflix.spinnaker.keel.api.schema.Optional
import java.time.Duration
import java.util.Collections.emptySortedSet
import java.util.SortedSet

data class ApplicationLoadBalancerV1_1Spec(
  override val moniker: Moniker,
  @Optional override val locations: SubnetAwareLocations,
  override val internal: Boolean = true,
  override val dependencies: LoadBalancerDependencies = LoadBalancerDependencies(),
  override val idleTimeout: Duration = Duration.ofSeconds(60),
  val listeners: Set<ListenerV1_1>,
  val targetGroups: Set<TargetGroup>,
  val overrides: Map<String, ApplicationLoadBalancerOverrideV1_1> = emptyMap()
) : LoadBalancerSpec {

  init {
    require(moniker.toString().length <= 32) {
      "load balancer names have a 32 character limit"
    }
  }

  override val loadBalancerType: LoadBalancerType = APPLICATION

  override val id: String = "${locations.account}:$moniker"

  data class ListenerV1_1(
    val port: Int,
    val protocol: String,
    val certificateArn: String?,
    val rules: Set<Rule> = emptySet(),
    val defaultActions: SortedSet<Action> = emptySortedSet()
  )

  data class ApplicationLoadBalancerOverrideV1_1(
    val dependencies: LoadBalancerDependencies? = null,
    val listeners: Set<ListenerV1_1>? = null,
    val targetGroups: Set<TargetGroup>? = null
  )
}
