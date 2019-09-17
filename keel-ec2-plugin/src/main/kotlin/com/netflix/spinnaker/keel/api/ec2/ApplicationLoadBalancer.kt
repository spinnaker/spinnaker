package com.netflix.spinnaker.keel.api.ec2

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.keel.clouddriver.model.ApplicationLoadBalancerModel.Action
import com.netflix.spinnaker.keel.clouddriver.model.ApplicationLoadBalancerModel.Rule
import com.netflix.spinnaker.keel.clouddriver.model.ApplicationLoadBalancerModel.TargetGroupAttributes
import com.netflix.spinnaker.keel.model.Moniker

data class ApplicationLoadBalancer(
  override val moniker: Moniker,
  override val location: Location,
  override val loadBalancerType: LoadBalancerType = LoadBalancerType.APPLICATION,
  override val internal: Boolean = true,
  override val vpcName: String?,
  override val subnetType: String?,
  override val securityGroupNames: Set<String> = emptySet(),
  override val idleTimeout: Int = 60,
  val listeners: Set<Listener>,
  val targetGroups: Set<TargetGroup>
) : LoadBalancer {

  init {
    require(moniker.name.length <= 32) {
      "load balancer names have a 32 character limit"
    }
  }

  @JsonIgnore
  override val id: String = "${location.accountName}:${location.region}:${moniker.name}"

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
    val healthCheckTimeoutSeconds: Int = 5,
    val healthCheckPort: Int = 7001,
    val healthCheckProtocol: String = "HTTP",
    val healthCheckHttpCode: String = "200-299",
    val healthCheckPath: String = "/healthcheck",
    val healthCheckIntervalSeconds: Int = 10,
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
