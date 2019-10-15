package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.model.Moniker
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
    val accountName: String,
    val vpcName: String,
    val region: String
  )
}
