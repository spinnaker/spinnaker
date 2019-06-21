package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.ec2.cluster.Location
import com.netflix.spinnaker.keel.model.Moniker

interface LoadBalancer {
  val moniker: Moniker
  val location: Location
  val loadBalancerType: LoadBalancerType
  val isInternal: Boolean
  val vpcName: String?
  val subnetType: String?
  val securityGroupNames: Set<String>
  val idleTimeout: Int
}
