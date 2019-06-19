package com.netflix.spinnaker.keel.clouddriver.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.keel.model.Moniker

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

  data class TargetGroup(
    val targetGroupName: String,
    val loadBalancerNames: List<String>,
    val targetType: String,
    val matcher: TargetGroupMatcher,
    val protocol: String,
    val port: Int,
    val healthCheckEnabled: Boolean,
    val healthCheckTimeoutSeconds: Int,
    val healthCheckPort: String,
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
    @JsonProperty("stickiness.enabled")
    val stickinessEnabled: Boolean = false,

    @JsonProperty("deregistration_delay.timeout_seconds")
    val deregistrationTimeout: Int = 600,

    @JsonProperty("stickiness.type")
    val stickinessType: String = "lb_cookie",

    @JsonProperty("stickiness.lb_cookie.duration_seconds")
    val stickinessDuration: Int = 86400,

    @JsonProperty("slow_start.duration_seconds")
    val slowStartDurationSeconds: Int = 0,

    @get:JsonAnyGetter val properties: Map<String, Any?> = emptyMap()
  )
}
