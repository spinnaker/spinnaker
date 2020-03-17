package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerSpec
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.plugins.SupportedKind
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ClassicLoadBalancerSecurityGroupsResolver : Resolver<ClassicLoadBalancerSpec> {
  override val supportedKind = SupportedKind(
    SPINNAKER_EC2_API_V1.qualify("classic-load-balancer"),
    ClassicLoadBalancerSpec::class.java
  )

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
