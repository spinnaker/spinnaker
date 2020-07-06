package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.UnhappyControl
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.Action
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.Listener
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.TargetGroup
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerType.APPLICATION
import java.time.Duration

data class ApplicationLoadBalancerSpec(
  override val moniker: Moniker,
  override val locations: SubnetAwareLocations,
  override val internal: Boolean = true,
  override val dependencies: LoadBalancerDependencies = LoadBalancerDependencies(),
  override val idleTimeout: Duration = Duration.ofSeconds(60),
  val listeners: Set<Listener>,
  val targetGroups: Set<TargetGroup>,
  val overrides: Map<String, ApplicationLoadBalancerOverride> = emptyMap()
) : LoadBalancerSpec, UnhappyControl {

  init {
    require(moniker.toString().length <= 32) {
      "load balancer names have a 32 character limit"
    }
  }

  override val maxDiffCount: Int? = 2

  // Once load balancers go unhappy, only retry when the diff changes, or if manually unvetoed
  override val unhappyWaitTime: Duration? = null

  override val loadBalancerType: LoadBalancerType = APPLICATION

  override val id: String = "${locations.account}:$moniker"

  data class Listener(
    val port: Int,
    val protocol: String,
    val certificateArn: String?,
    val rules: Set<Rule> = emptySet(),
    val defaultActions: Set<Action> = emptySet()
  )

  data class TargetGroup(
    val name: String,
    val targetType: String = "instance",
    val protocol: String = "HTTP",
    val port: Int,
    val healthCheckEnabled: Boolean = true,
    val healthCheckTimeout: Duration = Duration.ofSeconds(5),
    val healthCheckPort: Int = 7001,
    val healthCheckProtocol: String = "HTTP",
    val healthCheckHttpCode: String = "200-299",
    val healthCheckPath: String = "/healthcheck",
    val healthCheckInterval: Duration = Duration.ofSeconds(10),
    val healthyThresholdCount: Int = 10,
    val unhealthyThresholdCount: Int = 2,
    val attributes: TargetGroupAttributes = TargetGroupAttributes()
  ) {
    init {
      require(name.length <= 32) {
        "targetGroup names have a 32 character limit"
      }
    }
    override fun toString() = name
  }

  data class ApplicationLoadBalancerOverride(
    val dependencies: LoadBalancerDependencies? = null,
    val listeners: Set<Listener>? = null,
    val targetGroups: Set<TargetGroup>? = null
  )

  data class Action(
    val type: String,
    val order: Int,
    val targetGroupName: String?,
    val redirectConfig: RedirectConfig?
  )

  data class Rule(
    val priority: String,
    val conditions: List<Condition> = emptyList(),
    val actions: List<Action>,
    val default: Boolean
  )

  data class Condition(
    val field: String,
    val values: List<String>
  )

  data class RedirectConfig(
    val protocol: String,
    val port: String?,
    val host: String,
    val path: String,
    val query: String?,
    val statusCode: String
  )
}
