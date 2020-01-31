package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.LocationConstants
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import com.netflix.spinnaker.keel.plugin.Resolver
import org.springframework.stereotype.Component

abstract class NetworkResolver<T : Locatable<SubnetAwareLocations>>(
  private val cloudDriverCache: CloudDriverCache
) : Resolver<T> {

  protected fun SubnetAwareLocations.withResolvedNetwork(): SubnetAwareLocations {
    val resolvedSubnet = subnet ?: LocationConstants.DEFAULT_SUBNET_PURPOSE.format(vpc)
    return copy(
      vpc = vpc,
      subnet = resolvedSubnet,
      regions = regions.map { region ->
        if (region.availabilityZones.isEmpty()) {
          region.copy(
            availabilityZones = cloudDriverCache.resolveAvailabilityZones(
              account = account,
              subnet = resolvedSubnet,
              region = region
            )
          )
        } else {
          region
        }
      }.toSet()
    )
  }
}

@Component
class ClusterNetworkResolver(cloudDriverCache: CloudDriverCache) : NetworkResolver<ClusterSpec>(cloudDriverCache) {
  override val apiVersion: String = SPINNAKER_EC2_API_V1
  override val supportedKind: String = "cluster"

  override fun invoke(resource: Resource<ClusterSpec>): Resource<ClusterSpec> =
    resource.run {
      copy(
        spec = spec.run {
          copy(
            locations = locations.withResolvedNetwork()
          )
        }
      )
    }
}

@Component
class ClassicLoadBalancerNetworkResolver(cloudDriverCache: CloudDriverCache) : NetworkResolver<ClassicLoadBalancerSpec>(cloudDriverCache) {
  override val apiVersion: String = SPINNAKER_EC2_API_V1
  override val supportedKind: String = "classic-load-balancer"

  override fun invoke(resource: Resource<ClassicLoadBalancerSpec>): Resource<ClassicLoadBalancerSpec> =
    resource.run {
      copy(
        spec = spec.run {
          copy(
            locations = locations.withResolvedNetwork()
          )
        }
      )
    }
}

@Component
class ApplicationLoadBalancerNetworkResolver(cloudDriverCache: CloudDriverCache) : NetworkResolver<ApplicationLoadBalancerSpec>(cloudDriverCache) {
  override val apiVersion: String = SPINNAKER_EC2_API_V1
  override val supportedKind: String = "application-load-balancer"

  override fun invoke(resource: Resource<ApplicationLoadBalancerSpec>): Resource<ApplicationLoadBalancerSpec> =
    resource.run {
      copy(
        spec = spec.run {
          copy(
            locations = locations.withResolvedNetwork()
          )
        }
      )
    }
}

private fun CloudDriverCache.resolveAvailabilityZones(
  account: String,
  subnet: String,
  region: SubnetAwareRegionSpec
) =
  availabilityZonesBy(
    account,
    subnetBy(account, region.name, subnet).vpcId,
    subnet,
    region.name
  ).toSet()
