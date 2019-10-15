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
        region.copy(availabilityZones = cloudDriverCache.resolveAvailabilityZones(
          accountName = resource.spec.locations.accountName,
          subnetPurpose = resource.spec.locations.subnet ?: error("No subnet purpose specified or resolved"),
          region = region
        ))
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

private fun CloudDriverCache.resolveAvailabilityZones(accountName: String, subnetPurpose: String, region: SubnetAwareRegionSpec) =
  availabilityZonesBy(
    accountName,
    subnetBy(
      accountName,
      region.region,
      subnetPurpose
    ).vpcId,
    region.region
  ).toSet()
