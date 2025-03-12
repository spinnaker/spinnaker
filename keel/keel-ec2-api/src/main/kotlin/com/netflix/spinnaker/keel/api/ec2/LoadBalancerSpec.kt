package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import java.time.Duration

interface LoadBalancerSpec : Monikered, Locatable<SubnetAwareLocations> {
  val loadBalancerType: LoadBalancerType
  override val locations: SubnetAwareLocations
  val internal: Boolean
  val dependencies: LoadBalancerDependencies
  val idleTimeout: Duration
}
