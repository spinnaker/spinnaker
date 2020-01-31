package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.Moniker
import de.danielbechler.diff.inclusion.Inclusion.EXCLUDED
import de.danielbechler.diff.introspection.ObjectDiffProperty

data class SecurityGroup(
  val moniker: Moniker,
  val location: Location,
  @get:ObjectDiffProperty(inclusion = EXCLUDED)
  val description: String?,
  val inboundRules: Set<SecurityGroupRule> = emptySet()
) {
  data class Location(
    val account: String,
    val vpc: String,
    val region: String
  )
}
