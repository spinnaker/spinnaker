package com.netflix.spinnaker.keel.api.ec2

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerType.APPLICATION
import com.netflix.spinnaker.keel.clouddriver.model.ApplicationLoadBalancerModel.Action
import com.netflix.spinnaker.keel.clouddriver.model.ApplicationLoadBalancerModel.Rule
import com.netflix.spinnaker.keel.clouddriver.model.ApplicationLoadBalancerModel.TargetGroupAttributes
import java.time.Duration

data class ApplicationLoadBalancerSpec(
  override val moniker: Moniker,
  override val locations: SubnetAwareLocations,
  override val internal: Boolean = true,
  override val dependencies: LoadBalancerDependencies = LoadBalancerDependencies(),
  override val idleTimeout: Duration = Duration.ofSeconds(60),
  val listeners: Set<Listener>,
  val targetGroups: Set<TargetGroup>,
  @JsonInclude(NON_EMPTY)
  val overrides: Map<String, ApplicationLoadBalancerOverride> = emptyMap()
) : LoadBalancerSpec {

  init {
    require(moniker.toString().length <= 32) {
      "load balancer names have a 32 character limit"
    }
  }

  @JsonIgnore
  override val loadBalancerType: LoadBalancerType = APPLICATION

  @JsonIgnore
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
    val healthCheckTimeoutSeconds: Duration = Duration.ofSeconds(5),
    val healthCheckPort: Int = 7001,
    val healthCheckProtocol: String = "HTTP",
    val healthCheckHttpCode: String = "200-299",
    val healthCheckPath: String = "/healthcheck",
    val healthCheckIntervalSeconds: Duration = Duration.ofSeconds(10),
    val healthyThresholdCount: Int = 10,
    val unhealthyThresholdCount: Int = 2,
    val attributes: TargetGroupAttributes = TargetGroupAttributes()
  ) {
    init {
      require(name.length <= 32) {
        "targetGroup names have a 32 character limit"
      }
    }
  }
}

data class ApplicationLoadBalancerOverride(
  val dependencies: LoadBalancerDependencies? = null,
  val listeners: Set<ApplicationLoadBalancerSpec.Listener>? = null,
  val targetGroups: Set<ApplicationLoadBalancerSpec.TargetGroup>? = null
)
