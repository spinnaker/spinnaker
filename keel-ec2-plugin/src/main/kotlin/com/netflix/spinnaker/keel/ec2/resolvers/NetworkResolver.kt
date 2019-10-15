package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import com.netflix.spinnaker.keel.plugin.Resolver
import org.springframework.stereotype.Component

abstract class NetworkResolver<T : Locatable<*>> : Resolver<T> {

  protected fun SubnetAwareLocations.withResolvedNetwork(): SubnetAwareLocations {
    val resolvedVpcName: String =
      vpcName
        ?: subnet?.let { Regex("""^.+\((.+)\)$""").find(it)?.groupValues?.get(1) }
        ?: DEFAULT_VPC_NAME
    return copy(
      vpcName = resolvedVpcName,
      subnet = subnet ?: DEFAULT_SUBNET_PURPOSE.format(resolvedVpcName)
    )
  }

  protected fun SimpleLocations.withResolvedNetwork(): SimpleLocations {
    return copy(vpcName = vpcName ?: DEFAULT_VPC_NAME)
  }

  companion object {
    const val DEFAULT_VPC_NAME = "vpc0"
    const val DEFAULT_SUBNET_PURPOSE = "internal (%s)"
  }
}

@Component
class ClusterNetworkResolver : NetworkResolver<ClusterSpec>() {
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
class ClassicLoadBalancerNetworkResolver : NetworkResolver<ClassicLoadBalancerSpec>() {
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
class ApplicationLoadBalancerNetworkResolver : NetworkResolver<ApplicationLoadBalancerSpec>() {
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
