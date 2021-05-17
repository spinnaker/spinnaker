package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.UnhappyControl
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerType.APPLICATION
import com.netflix.spinnaker.keel.api.schema.Discriminator
import com.netflix.spinnaker.keel.api.schema.Optional
import java.time.Duration

data class ApplicationLoadBalancerSpec(
  override val moniker: Moniker,
  @Optional override val locations: SubnetAwareLocations,
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
    val certificate: String? = null,
    val rules: Set<Rule> = emptySet(),
    val defaultActions: Set<Action> = emptySet()
  ) {
    init {
      if (protocol == "HTTPS") {
        requireNotNull(certificate) {
          "HTTPS listeners must specify a certificate"
        }
      }
    }
  }

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

  abstract class Action {
    @Discriminator
    abstract val type: String
    abstract val order: Int

    data class ForwardAction(
      override val order: Int,
      val targetGroupName: String
    ) : Action() {
      override val type = "forward"
    }

    data class RedirectAction(
      override val order: Int,
      val redirectActionConfig: RedirectConfig
    ) : Action() {
      override val type = "redirect"
    }

    data class AuthenticateOidcAction(
      override val order: Int,
      val authenticateOidcConfig: AuthenticateOidcActionConfig
    ) : Action() {
      override val type = "authenticate-oidc"
    }
  }

  data class Rule(
    val priority: String,
    val conditions: List<Condition> = emptyList(),
    val actions: List<Action>,
    val default: Boolean
  )

  data class Condition(
    val field: String,
    val values: List<String>,
  )

  data class RedirectConfig(
    val protocol: String,
    val host: String,
    val port: String?,
    val path: String,
    val query: String?,
    val statusCode: String
  )

  data class AuthenticateOidcActionConfig(
    val issuer: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val userInfoEndpoint: String,
    val clientId: String,
    val sessionCookieName: String,
    val scope: String,
    val sessionTimeout: Duration,
    val authenticationRequestExtraParams: Map<String, Any?> = emptyMap()
  )
}
