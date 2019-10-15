package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.plugin.Resolver

abstract class AvailabilityZonesResolver<T : Locatable<SubnetAwareLocations>>(
  private val cloudDriverCache: CloudDriverCache
) : Resolver<T> {

  override fun invoke(resource: Resource<T>): Resource<T> {
    val regions = resource.spec.locations.regions.map { region ->
      if (region.availabilityZones.isEmpty()) {
        region.copy(availabilityZones = cloudDriverCache.resolveAvailabilityZones(
          account = resource.spec.locations.account,
          subnet = resource.spec.locations.subnet ?: error("No subnet purpose specified or resolved"),
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

  protected abstract fun T.withLocations(locations: SubnetAwareLocations): T
}

private fun CloudDriverCache.resolveAvailabilityZones(account: String, subnet: String, region: SubnetAwareRegionSpec) =
  availabilityZonesBy(
    account,
    subnetBy(
      account,
      region.name,
      subnet
    ).vpcId,
    region.name
  ).toSet()
