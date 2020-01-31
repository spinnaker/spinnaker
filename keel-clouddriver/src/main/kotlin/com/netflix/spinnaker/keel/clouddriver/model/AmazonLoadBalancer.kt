package com.netflix.spinnaker.keel.clouddriver.model

import com.netflix.spinnaker.keel.api.Moniker

interface AmazonLoadBalancer {
  val moniker: Moniker?
  val loadBalancerName: String
  val loadBalancerType: String
  val availabilityZones: Set<String>
  val vpcId: String
  val subnets: Set<String>
  val scheme: String?
  val idleTimeout: Int
  val securityGroups: Set<String>
}
