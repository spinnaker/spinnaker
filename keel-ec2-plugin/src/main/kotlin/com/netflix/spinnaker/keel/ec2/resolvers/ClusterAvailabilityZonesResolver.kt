package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.model.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.plugin.Resolver
import org.springframework.stereotype.Component

@Component
class ClusterAvailabilityZonesResolver(
  private val cloudDriverCache: CloudDriverCache
) : Resolver<ClusterSpec> {

  override val apiVersion = SPINNAKER_API_V1.subApi("ec2")
  override val supportedKind = "cluster"

  override fun invoke(resource: Resource<ClusterSpec>): Resource<ClusterSpec> {
    val regions = resource.spec.locations.regions.map { region ->
      if (region.availabilityZones.isEmpty()) {
        region.copy(availabilityZones = cloudDriverCache.resolveAvailabilityZones(resource.spec.locations.accountName, region))
      } else {
        region
      }
    }
    return resource.copy(
      spec = resource.spec.copy(
        locations = resource.spec.locations.copy(
          regions = regions.toSet())
      )
    )
  }
}

private fun CloudDriverCache.resolveAvailabilityZones(accountName: String, region: SubnetAwareRegionSpec) =
  availabilityZonesBy(
    accountName,
    subnetBy(accountName, region.region, region.subnet).vpcId,
    region.region
  ).toSet()
