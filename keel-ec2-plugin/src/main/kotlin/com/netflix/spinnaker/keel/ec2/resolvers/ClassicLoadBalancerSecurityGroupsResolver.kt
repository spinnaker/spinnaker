package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerSpec
import com.netflix.spinnaker.keel.plugin.Resolver
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ClassicLoadBalancerSecurityGroupsResolver : Resolver<ClassicLoadBalancerSpec> {
  override val apiVersion = SPINNAKER_API_V1.subApi("ec2")
  override val supportedKind = "classic-load-balancer"

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun invoke(resource: Resource<ClassicLoadBalancerSpec>): Resource<ClassicLoadBalancerSpec> =
    if (resource.spec.dependencies.securityGroupNames.isEmpty()) {
      resource.run {
        copy(spec = spec.run {
          copy(dependencies = dependencies.run {
            copy(securityGroupNames = setOf("${moniker.app}-elb"))
          })
        })
      }
    } else {
      resource
    }
}
