package com.netflix.spinnaker.keel.ec2

import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.Action
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.Action.AuthenticateOidcAction
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.Action.ForwardAction
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.Action.RedirectAction
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.Condition
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.RedirectConfig
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.Rule
import com.netflix.spinnaker.keel.api.ec2.ServerGroup
import com.netflix.spinnaker.keel.api.ec2.TargetGroupAttributes
import com.netflix.spinnaker.keel.clouddriver.model.ActiveServerGroupImage
import com.netflix.spinnaker.keel.clouddriver.model.ApplicationLoadBalancerModel
import com.netflix.spinnaker.keel.clouddriver.model.BuildInfo
import com.netflix.spinnaker.keel.clouddriver.model.InstanceCounts
import java.time.Duration

internal fun ApplicationLoadBalancerModel.Rule.toEc2Api(): Rule =
  Rule(
    priority = priority,
    conditions = conditions?.map { it.toEc2Api() } ?: emptyList(),
    actions = actions.map { it.toEc2Api() }.toSortedSet(),
    default = default
  )

internal fun ApplicationLoadBalancerModel.Condition.toEc2Api(): Condition =
  Condition(field, values)

internal fun ApplicationLoadBalancerModel.Action.toEc2Api(): Action =
  when(type) {
    "redirect" -> RedirectAction(order, checkNotNull(redirectConfig).toEc2Api())
    "authenticate-oidc" -> AuthenticateOidcAction(order, checkNotNull(authenticateOidcConfig).toEc2Api())
    "forward" -> ForwardAction(order, checkNotNull(targetGroupName))
    else -> throw UnsupportedActionType(type)
  }

internal fun ApplicationLoadBalancerModel.RedirectConfig.toEc2Api(): RedirectConfig =
  RedirectConfig(protocol, host, port, path, query, statusCode)

internal fun ApplicationLoadBalancerModel.TargetGroupAttributes.toEc2Api(): TargetGroupAttributes =
  TargetGroupAttributes(stickinessEnabled, deregistrationDelay, stickinessType, stickinessDuration, slowStartDurationSeconds, properties)

internal fun BuildInfo.toEc2Api(): ServerGroup.BuildInfo =
  ServerGroup.BuildInfo(packageName)

internal fun ActiveServerGroupImage.toEc2Api(): ServerGroup.ActiveServerGroupImage =
  ServerGroup.ActiveServerGroupImage(imageId, appVersion, baseImageName, name, imageLocation, description)

internal fun ApplicationLoadBalancerModel.AuthenticateOidcConfig.toEc2Api(): ApplicationLoadBalancerSpec.AuthenticateOidcActionConfig =
  ApplicationLoadBalancerSpec.AuthenticateOidcActionConfig(issuer, authorizationEndpoint, tokenEndpoint, userInfoEndpoint, clientId, sessionCookieName, scope, Duration.ofSeconds(sessionTimeout), authenticationRequestExtraParams)

internal fun InstanceCounts.toEc2Api(): ServerGroup.InstanceCounts =
  ServerGroup.InstanceCounts(total, up, down, unknown, outOfService, starting)

class UnsupportedActionType(type: String) : IllegalArgumentException("Action type \"$type\" is not currently supported")

fun Action.toOrcaRequest(): Map<String, Any?> =
  mapOf(
    "type" to type,
    "order" to order,
  ) + when (this) {
    is ForwardAction -> mapOf(
      "targetGroupName" to targetGroupName
    )
    is RedirectAction -> mapOf(
      "redirectActionConfig" to redirectConfig
    )
    is AuthenticateOidcAction -> mapOf(
      "authenticateOidcActionConfig" to mapOf(
        "issuer" to authenticateOidcConfig.issuer,
        "authorizationEndpoint" to authenticateOidcConfig.authorizationEndpoint,
        "tokenEndpoint" to authenticateOidcConfig.tokenEndpoint,
        "userInfoEndpoint" to authenticateOidcConfig.userInfoEndpoint,
        "clientId" to authenticateOidcConfig.clientId,
        "sessionCookieName" to authenticateOidcConfig.sessionCookieName,
        "scope" to authenticateOidcConfig.scope,
        "sessionTimeout" to authenticateOidcConfig.sessionTimeout.toSeconds(),
        "authenticationRequestExtraParams" to authenticateOidcConfig.authenticationRequestExtraParams,
      )
    )
    else -> emptyMap()
  }
