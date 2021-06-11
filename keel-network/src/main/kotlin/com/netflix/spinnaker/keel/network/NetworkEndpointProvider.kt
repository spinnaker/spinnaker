package com.netflix.spinnaker.keel.network

import com.netflix.spinnaker.config.DnsConfig
import com.netflix.spinnaker.keel.api.ComputeResourceSpec
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.network.NetworkEndpointType.DNS
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
  private val dnsConfig: DnsConfig
) {
  fun getNetworkEndpoints(resource: Resource<*>): Set<NetworkEndpoint> {
    with(resource.spec) {
      return when (this) {
        is ComputeResourceSpec<*> -> {
          locations.regions.flatMap { region ->
            listOf(
              // Example: lpollolocaltest-feature-preview.vip.us-east-1.test.acme.net
              NetworkEndpoint(DNS, region.name, "${moniker.toName()}.vip.${region.name}.${locations.account.environment}.${dnsConfig.defaultDomain}"),
              NetworkEndpoint(DNS, region.name, "${moniker.toName()}.cluster.${region.name}.${locations.account.environment}.${dnsConfig.defaultDomain}"),
            )
          }.toSet()
        }
        is LoadBalancerSpec -> {
          locations.regions.map { region ->
            // Example: internal-keel-test-vpc0-1234567890.us-west-2.elb.amazonaws.com
            val address = (if (internal) "internal-" else "") +
              "${moniker.toName()}-${locations.vpc ?: "vpc0"}-${locations.account.id}.${region.name}.elb.amazonaws.com"
            NetworkEndpoint(DNS, region.name, address)
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

  private val String.id: String
    get() = runBlocking {
      cloudDriverCache.credentialBy(this@id).attributes["accountId"] as? String ?: this@id
    }
}
