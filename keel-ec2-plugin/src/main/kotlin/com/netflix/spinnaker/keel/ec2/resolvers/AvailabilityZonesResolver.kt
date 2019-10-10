package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Locations
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.model.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.plugin.Resolver

abstract class AvailabilityZonesResolver<T : Locatable<SubnetAwareRegionSpec>>(
  private val cloudDriverCache: CloudDriverCache
) : Resolver<T> {

  override fun invoke(resource: Resource<T>): Resource<T> {
    val regions = resource.spec.locations.regions.map { region ->
      if (region.availabilityZones.isEmpty()) {
        region.copy(availabilityZones = cloudDriverCache.resolveAvailabilityZones(resource.spec.locations.accountName, region))
      } else {
        region
      }
    }
    return resource.run {
      copy(spec = spec.withLocations(spec.locations.copy(regions = regions.toSet())))
    }
  }

  protected abstract fun T.withLocations(locations: Locations<SubnetAwareRegionSpec>): T
}

private fun CloudDriverCache.resolveAvailabilityZones(accountName: String, region: SubnetAwareRegionSpec) =
  availabilityZonesBy(
    accountName,
    subnetBy(accountName, region.region, region.subnet).vpcId,
    region.region
  ).toSet()
