package com.netflix.spinnaker.keel.ec2.normalizers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancer
import com.netflix.spinnaker.keel.plugin.ResourceNormalizer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ClassicLoadBalancerNormalizer(
  private val objectMapper: ObjectMapper
) : ResourceNormalizer<ClassicLoadBalancer> {
  override val apiVersion = SPINNAKER_API_V1.subApi("ec2")
  override val supportedKind = "classic-load-balancer"

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun normalize(resource: Resource<*>): Resource<ClassicLoadBalancer> {
    @Suppress("UNCHECKED_CAST")
    val r = resource as Resource<ClassicLoadBalancer>

    if (r.spec.securityGroupNames.isEmpty()) {
      val securityGroupNames = setOf("${r.spec.moniker.app}-elb")

      val spec: ClassicLoadBalancer = r.spec.copy(securityGroupNames = securityGroupNames)
      return r.copy(spec = spec)
    }

    return r
  }
}
