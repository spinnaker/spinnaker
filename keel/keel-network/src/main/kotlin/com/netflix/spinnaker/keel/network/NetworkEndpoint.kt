package com.netflix.spinnaker.keel.network

/**
 * A network endpoint one can connect to on a [Resource].
 *
 * @see NetworkEndpointProvider
 */
data class NetworkEndpoint(
  val type: NetworkEndpointType,
  val region: String,
  val address: String
)

enum class NetworkEndpointType {
  EUREKA_VIP_DNS, EUREKA_CLUSTER_DNS, DNS, IPV4, IPV6;
}
