package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Locations
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.RegionSpec
import com.netflix.spinnaker.keel.model.SimpleRegionSpec

interface LoadBalancer : Monikered, Locatable {
  val location: Location
  val loadBalancerType: LoadBalancerType
  val internal: Boolean
  val vpcName: String?
  val subnetType: String?
  val securityGroupNames: Set<String>
  val idleTimeout: Int

  override val locations: Locations<out RegionSpec>
    get() = Locations(
      accountName = location.accountName,
      regions = setOf(SimpleRegionSpec(location.region))
    )
}
