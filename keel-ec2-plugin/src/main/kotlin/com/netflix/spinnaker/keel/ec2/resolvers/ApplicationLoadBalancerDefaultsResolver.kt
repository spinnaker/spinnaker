package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.clouddriver.model.ApplicationLoadBalancerModel
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import org.springframework.stereotype.Component

@Component
class ApplicationLoadBalancerDefaultsResolver : Resolver<ApplicationLoadBalancerSpec> {
  override val apiVersion = SPINNAKER_EC2_API_V1
  override val supportedKind = "application-load-balancer"

  override fun invoke(resource: Resource<ApplicationLoadBalancerSpec>): Resource<ApplicationLoadBalancerSpec> {
    if (resource.spec.listeners.any { it.defaultActions.isEmpty() } || resource.spec.dependencies.securityGroupNames.isEmpty()) {
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

          ApplicationLoadBalancerSpec.Listener(
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

      val securityGroupNames = if (resource.spec.dependencies.securityGroupNames.isEmpty()) {
        setOf("${resource.spec.moniker.app}-elb")
      } else {
        resource.spec.dependencies.securityGroupNames
      }

      return resource.run {
        copy(spec = spec.run {
          copy(listeners = listeners, dependencies = dependencies.run {
            copy(securityGroupNames = securityGroupNames)
          })
        })
      }
    }

    return resource
  }
}
