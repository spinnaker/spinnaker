package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import org.springframework.stereotype.Component

@Component
class ApplicationLoadBalancerAvailabilityZonesResolver(
  cloudDriverCache: CloudDriverCache
) : AvailabilityZonesResolver<ApplicationLoadBalancerSpec>(cloudDriverCache) {

  override val apiVersion = SPINNAKER_EC2_API_V1
  override val supportedKind = "application-load-balancer"

  override fun ApplicationLoadBalancerSpec.withLocations(locations: SubnetAwareLocations): ApplicationLoadBalancerSpec =
    copy(locations = locations)
}
