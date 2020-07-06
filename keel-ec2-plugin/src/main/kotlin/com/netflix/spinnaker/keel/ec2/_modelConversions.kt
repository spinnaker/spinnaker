package com.netflix.spinnaker.keel.ec2

import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.Action
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.Condition
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.RedirectConfig
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.Rule
import com.netflix.spinnaker.keel.api.ec2.ServerGroup
import com.netflix.spinnaker.keel.api.ec2.TargetGroupAttributes
import com.netflix.spinnaker.keel.clouddriver.model.ActiveServerGroupImage
import com.netflix.spinnaker.keel.clouddriver.model.ApplicationLoadBalancerModel
import com.netflix.spinnaker.keel.clouddriver.model.BuildInfo
import com.netflix.spinnaker.keel.clouddriver.model.InstanceCounts

internal fun ApplicationLoadBalancerModel.Rule.toEc2Api(): Rule =
  Rule(
    priority = priority,
    conditions = conditions?.map { it.toEc2Api() } ?: emptyList(),
    actions = actions.map { it.toEc2Api() },
    default = default
  )

internal fun ApplicationLoadBalancerModel.Condition.toEc2Api(): Condition =
  Condition(field, values)

internal fun ApplicationLoadBalancerModel.Action.toEc2Api(): Action =
  Action(type, order, targetGroupName, redirectConfig?.toEc2Api())

internal fun ApplicationLoadBalancerModel.RedirectConfig.toEc2Api(): RedirectConfig =
  RedirectConfig(protocol, port, host, path, query, statusCode)

internal fun ApplicationLoadBalancerModel.TargetGroupAttributes.toEc2Api(): TargetGroupAttributes =
  TargetGroupAttributes(stickinessEnabled, deregistrationDelay, stickinessType, stickinessDuration, slowStartDurationSeconds, properties)

internal fun BuildInfo.toEc2Api(): ServerGroup.BuildInfo =
  ServerGroup.BuildInfo(packageName)

internal fun ActiveServerGroupImage.toEc2Api(): ServerGroup.ActiveServerGroupImage =
  ServerGroup.ActiveServerGroupImage(imageId, appVersion, baseImageVersion, name, imageLocation, description)

internal fun InstanceCounts.toEc2Api(): ServerGroup.InstanceCounts =
  ServerGroup.InstanceCounts(total, up, down, unknown, outOfService, starting)
