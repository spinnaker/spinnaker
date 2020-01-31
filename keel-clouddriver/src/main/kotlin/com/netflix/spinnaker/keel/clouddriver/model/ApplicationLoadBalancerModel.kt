package com.netflix.spinnaker.keel.clouddriver.model

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.netflix.spinnaker.keel.api.Moniker

data class ApplicationLoadBalancerModel(
  override val moniker: Moniker?,
  override val loadBalancerName: String,
  override val loadBalancerType: String = "application",
  override val availabilityZones: Set<String>,
  override val vpcId: String,
  override val subnets: Set<String>,
  override val scheme: String?,
  override val idleTimeout: Int,
  override val securityGroups: Set<String>,
  val listeners: List<ApplicationLoadBalancerListener>,
  val targetGroups: List<TargetGroup>,
  val ipAddressType: String,
  @get:JsonAnyGetter val properties: Map<String, Any?> = emptyMap()
) : AmazonLoadBalancer {
  data class ApplicationLoadBalancerListener(
    val port: Int,
    val protocol: String,
    val certificates: List<Certificate>?,
    val defaultActions: List<Action>,
    val rules: List<Rule>
  )

  data class Certificate(
    val certificateArn: String
  )

  data class Action(
    val type: String,
    val order: Int,
    val targetGroupName: String,
    val redirectConfig: RedirectConfig?
  )

  data class Rule(
    val priority: String,
    val conditions: List<Condition>?,
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

  data class TargetGroup(
    val targetGroupName: String,
    val loadBalancerNames: List<String>,
    val targetType: String,
    val matcher: TargetGroupMatcher,
    val protocol: String,
    val port: Int,
    val healthCheckEnabled: Boolean,
    val healthCheckTimeoutSeconds: Int,
    val healthCheckPort: Int,
    val healthCheckProtocol: String,
    val healthCheckPath: String,
    val healthCheckIntervalSeconds: Int,
    val healthyThresholdCount: Int,
    val unhealthyThresholdCount: Int,
    val vpcId: String,
    val attributes: TargetGroupAttributes,
    @get:JsonAnyGetter val properties: Map<String, Any?> = emptyMap()
  )

  data class TargetGroupMatcher(
    val httpCode: String
  )

  data class TargetGroupAttributes(
    @JsonAlias("stickiness.enabled")
    val stickinessEnabled: Boolean = false,

    @JsonAlias("deregistration_delay.timeout_seconds")
    val deregistrationDelay: Int = 300,

    @JsonAlias("stickiness.type")
    val stickinessType: String = "lb_cookie",

    @JsonAlias("stickiness.lb_cookie.duration_seconds")
    val stickinessDuration: Int = 86400,

    @JsonAlias("slow_start.duration_seconds")
    val slowStartDurationSeconds: Int = 0,

    @get:JsonAnyGetter val properties: Map<String, Any?> = emptyMap()
  )
}
