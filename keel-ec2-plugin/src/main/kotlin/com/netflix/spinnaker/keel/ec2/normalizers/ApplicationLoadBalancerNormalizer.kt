package com.netflix.spinnaker.keel.ec2.normalizers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancer
import com.netflix.spinnaker.keel.clouddriver.model.ApplicationLoadBalancerModel
import com.netflix.spinnaker.keel.plugin.ResourceNormalizer
import org.springframework.stereotype.Component

@Component
class ApplicationLoadBalancerNormalizer(
  private val objectMapper: ObjectMapper
) : ResourceNormalizer<ApplicationLoadBalancer> {
  override val apiVersion = SPINNAKER_API_V1.subApi("ec2")
  override val supportedKind = "application-load-balancer"

  override fun normalize(resource: Resource<*>): Resource<ApplicationLoadBalancer> {
    @Suppress("UNCHECKED_CAST")
    val r = resource as Resource<ApplicationLoadBalancer>

    if (r.spec.listeners.any { it.defaultActions.isEmpty() } || r.spec.securityGroupNames.isEmpty()) {
      val listeners = r.spec.listeners.map {
        if (it.defaultActions.isEmpty()) {
          val defaultActions = if (it.defaultActions.isEmpty()) {
            setOf(
              ApplicationLoadBalancerModel.Action(
                type = "forward",
                order = 1,
                targetGroupName = r.spec.targetGroups.first().name,
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

      val securityGroupNames = if (r.spec.securityGroupNames.isEmpty()) {
        setOf("${r.spec.moniker.app}-elb")
      } else {
        r.spec.securityGroupNames
      }

      val spec: ApplicationLoadBalancer = r.spec.copy(listeners = listeners, securityGroupNames = securityGroupNames)
      return r.copy(spec = spec)
    }

    return r
  }
}
