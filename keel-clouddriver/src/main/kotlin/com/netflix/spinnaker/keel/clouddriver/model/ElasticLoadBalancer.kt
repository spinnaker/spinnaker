package com.netflix.spinnaker.keel.clouddriver.model

import com.netflix.spinnaker.keel.model.Listener
import com.netflix.spinnaker.keel.model.Scheme

data class ElasticLoadBalancer(
  val loadBalancerName: String,
  val scheme: Scheme?,
  val vpcid: String?,
  val availabilityZones: Set<String>,
  val securityGroups: Set<String>,
  val healthCheck: HealthCheck,
  val listenerDescriptions: Set<ListenerDescription>
) {
  data class ListenerDescription(
    val listener: Listener
  )

  data class HealthCheck(
    val target: String,
    val interval: Int,
    val timeout: Int,
    val unhealthyThreshold: Int,
    val healthyThreshold: Int
  )
}
