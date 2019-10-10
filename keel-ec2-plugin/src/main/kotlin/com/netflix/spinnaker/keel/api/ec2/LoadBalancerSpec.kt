package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Locations
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.model.SubnetAwareRegionSpec
import java.time.Duration

interface LoadBalancerSpec : Monikered, Locatable<SubnetAwareRegionSpec> {
  val loadBalancerType: LoadBalancerType
  override val locations: Locations<SubnetAwareRegionSpec>
  val internal: Boolean
  val vpcName: String? // TODO: belongs on locations? Or can we derive this from subnet?
  val dependencies: LoadBalancerDependencies
  val idleTimeout: Duration
}
