package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.ExcludedFromDiff
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.support.Tag

data class SecurityGroup(
  val moniker: Moniker,
  val location: Location,
  @get:ExcludedFromDiff
  val description: String? = moniker.toString(),
  val inboundRules: Set<SecurityGroupRule> = emptySet(),
  @get:ExcludedFromDiff
  val tags: Set<Tag> = emptySet()
) {
  data class Location(
    val account: String,
    val vpc: String,
    val region: String
  )
}
