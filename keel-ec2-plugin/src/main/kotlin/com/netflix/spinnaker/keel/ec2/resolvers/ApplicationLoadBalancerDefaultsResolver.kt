package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancer
import com.netflix.spinnaker.keel.clouddriver.model.ApplicationLoadBalancerModel
import com.netflix.spinnaker.keel.plugin.Resolver
import org.springframework.stereotype.Component

@Component
class ApplicationLoadBalancerDefaultsResolver : Resolver<ApplicationLoadBalancer> {
  override val apiVersion = SPINNAKER_API_V1.subApi("ec2")
  override val supportedKind = "application-load-balancer"

  override fun invoke(resource: Resource<ApplicationLoadBalancer>): Resource<ApplicationLoadBalancer> {
    if (resource.spec.listeners.any { it.defaultActions.isEmpty() } || resource.spec.securityGroupNames.isEmpty()) {
      val listeners = resource.spec.listeners.map {
        if (it.defaultActions.isEmpty()) {
          val defaultActions = if (it.defaultActions.isEmpty()) {
            setOf(
              ApplicationLoadBalancerModel.Action(
                type = "forward",
                order = 1,
                targetGroupName = resource.spec.targetGroups.first().name,
                redirectConfig = null
              )
            )
          } else {
            it.defaultActions
          }

          ApplicationLoadBalancer.Listener(
            port = it.port,
            protocol = it.protocol,
            certificateArn = it.certificateArn,
            // TODO: The default rule can only be written via clouddriver as a defaultAction which seems like a bug.
            //  When an ALB is read from clouddriver, the default action appears under both defaultAction and as a rule.
            //  UpsertAmazonLoadBalancerV2Description doesn't allow setting isDefault on Rules which may be the issue.
            rules = if (it.rules.any { r -> !r.default }) {
              it.rules.filter { r -> !r.default }
                .toSet()
            } else {
              emptySet()
            },
            defaultActions = defaultActions
          )
        } else {
          it
        }
      }
        .toSet()

      val securityGroupNames = if (resource.spec.securityGroupNames.isEmpty()) {
        setOf("${resource.spec.moniker.app}-elb")
      } else {
        resource.spec.securityGroupNames
      }

      val spec = resource.spec.copy(listeners = listeners, securityGroupNames = securityGroupNames)
      return resource.copy(spec = spec)
    }

    return resource
  }
}
