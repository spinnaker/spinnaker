package com.netflix.spinnaker.keel.network

import com.netflix.spinnaker.config.DnsConfig
import com.netflix.spinnaker.keel.api.ComputeResourceSpec
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.network.NetworkEndpointType.DNS
import com.netflix.spinnaker.keel.network.NetworkEndpointType.EUREKA_CLUSTER_DNS
import com.netflix.spinnaker.keel.network.NetworkEndpointType.EUREKA_VIP_DNS
import kotlinx.coroutines.runBlocking
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Provides network endpoints for certain resource types based on configuration and runtime information
 * available from CloudDriver.
 */
@Component
@EnableConfigurationProperties(DnsConfig::class)
class NetworkEndpointProvider(
  private val cloudDriverCache: CloudDriverCache,
  private val cloudDriverService: CloudDriverService,
  private val dnsConfig: DnsConfig
) {
  suspend fun getNetworkEndpoints(resource: Resource<*>): Set<NetworkEndpoint> {
    with(resource.spec) {
      return when (this) {
        is ComputeResourceSpec<*> -> {
          locations.regions.flatMap { region ->
            listOf(
              // Example: lpollolocaltest-feature-preview.vip.us-east-1.test.acme.net
              NetworkEndpoint(EUREKA_VIP_DNS, region.name, "${moniker.toName()}.vip.${region.name}.${locations.account.environment}.${dnsConfig.defaultDomain}"),
              NetworkEndpoint(EUREKA_CLUSTER_DNS, region.name, "${moniker.toName()}.cluster.${region.name}.${locations.account.environment}.${dnsConfig.defaultDomain}"),
            )
          }.toSet()
        }
        is LoadBalancerSpec -> {
          locations.regions.mapNotNull { region ->
            // Example: internal-keel-test-vpc0-1234567890.us-west-2.elb.amazonaws.com
            cloudDriverService.getAmazonLoadBalancer(
              user = DEFAULT_SERVICE_ACCOUNT,
              account = locations.account,
              region = region.name,
              name = moniker.toName()
            ).firstOrNull()
              ?.let { NetworkEndpoint(DNS, region.name, it.dnsName) }
          }.toSet()
        }
        else -> emptySet()
      }
    }
  }

  private val String.environment: String
    get() = runBlocking {
      cloudDriverCache.credentialBy(this@environment).environment
    }
}
