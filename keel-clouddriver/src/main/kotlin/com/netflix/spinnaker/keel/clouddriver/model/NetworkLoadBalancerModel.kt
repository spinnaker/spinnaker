package com.netflix.spinnaker.keel.clouddriver.model

import com.netflix.spinnaker.keel.api.Moniker

data class NetworkLoadBalancerModel(
  override val moniker: Moniker?,
  override val loadBalancerName: String,
  override val loadBalancerType: String = "network",
  override val vpcId: String,
  override val subnets: Set<String>,
  override val scheme: String?,
  override val availabilityZones: Set<String>,
  val targetGroups: List<TargetGroup>
) : AmazonLoadBalancer {
  data class TargetGroup(
    val targetGroupName: String
  )
}
