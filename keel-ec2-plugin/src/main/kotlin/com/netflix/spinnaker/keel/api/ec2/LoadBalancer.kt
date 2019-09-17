package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.Monikered

interface LoadBalancer : Monikered {
  val location: Location
  val loadBalancerType: LoadBalancerType
  val internal: Boolean
  val vpcName: String?
  val subnetType: String?
  val securityGroupNames: Set<String>
  val idleTimeout: Int
}
