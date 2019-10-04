package com.netflix.spinnaker.keel.ec2.normalizers

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancer
import com.netflix.spinnaker.keel.plugin.Resolver
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ClassicLoadBalancerSecurityGroupsResolver : Resolver<ClassicLoadBalancer> {
  override val apiVersion = SPINNAKER_API_V1.subApi("ec2")
  override val supportedKind = "classic-load-balancer"

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun invoke(resource: Resource<ClassicLoadBalancer>): Resource<ClassicLoadBalancer> {
    if (resource.spec.securityGroupNames.isEmpty()) {
      val securityGroupNames = setOf("${resource.spec.moniker.app}-elb")

      val spec = resource.spec.copy(securityGroupNames = securityGroupNames)
      return resource.copy(spec = spec)
    }

    return resource
  }
}
