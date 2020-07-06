package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.ExcludedFromDiff
import com.netflix.spinnaker.keel.api.Moniker

data class SecurityGroup(
  val moniker: Moniker,
  val location: Location,
  @get:ExcludedFromDiff
  val description: String?,
  val inboundRules: Set<SecurityGroupRule> = emptySet()
) {
  data class Location(
    val account: String,
    val vpc: String,
    val region: String
  )
}
