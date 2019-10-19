package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import com.netflix.spinnaker.keel.plugin.Resolver
import org.springframework.stereotype.Component

abstract class NetworkResolver<T : Locatable<*>>(
  private val cloudDriverCache: CloudDriverCache
) : Resolver<T> {

  protected fun SubnetAwareLocations.withResolvedNetwork(): SubnetAwareLocations {
    val resolvedVpcName: String =
      vpc
        ?: subnet?.let { Regex("""^.+\((.+)\)$""").find(it)?.groupValues?.get(1) }
        ?: DEFAULT_VPC_NAME
    val resolvedSubnet = subnet ?: DEFAULT_SUBNET_PURPOSE.format(resolvedVpcName)
    return copy(
      vpc = resolvedVpcName,
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

  protected fun SimpleLocations.withResolvedNetwork(): SimpleLocations =
    copy(vpc = vpc ?: DEFAULT_VPC_NAME)

  companion object {
    const val DEFAULT_VPC_NAME = "vpc0"
    const val DEFAULT_SUBNET_PURPOSE = "internal (%s)"
  }
}

@Component
class SecurityGroupNetworkResolver(cloudDriverCache: CloudDriverCache) : NetworkResolver<SecurityGroupSpec>(cloudDriverCache) {
  override val apiVersion: ApiVersion = SPINNAKER_EC2_API_V1
  override val supportedKind: String = "security-group"

  override fun invoke(resource: Resource<SecurityGroupSpec>): Resource<SecurityGroupSpec> =
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
class ClusterNetworkResolver(cloudDriverCache: CloudDriverCache) : NetworkResolver<ClusterSpec>(cloudDriverCache) {
  override val apiVersion: ApiVersion = SPINNAKER_EC2_API_V1
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
  override val apiVersion: ApiVersion = SPINNAKER_EC2_API_V1
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
  override val apiVersion: ApiVersion = SPINNAKER_EC2_API_V1
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
    region.name
  ).toSet()
