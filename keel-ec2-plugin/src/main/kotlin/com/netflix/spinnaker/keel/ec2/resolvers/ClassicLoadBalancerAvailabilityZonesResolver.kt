package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import org.springframework.stereotype.Component

@Component
class ClassicLoadBalancerAvailabilityZonesResolver(
  cloudDriverCache: CloudDriverCache
) : AvailabilityZonesResolver<ClassicLoadBalancerSpec>(cloudDriverCache) {

  override val apiVersion = SPINNAKER_EC2_API_V1
  override val supportedKind = "classic-load-balancer"

  override fun ClassicLoadBalancerSpec.withLocations(locations: SubnetAwareLocations): ClassicLoadBalancerSpec =
    copy(locations = locations)
}
