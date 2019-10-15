package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import org.springframework.stereotype.Component

@Component
class ClusterAvailabilityZonesResolver(
  cloudDriverCache: CloudDriverCache
) : AvailabilityZonesResolver<ClusterSpec>(cloudDriverCache) {
  override val apiVersion = SPINNAKER_EC2_API_V1
  override val supportedKind = "cluster"

  override fun ClusterSpec.withLocations(locations: SubnetAwareLocations): ClusterSpec =
    copy(locations = locations)
}
