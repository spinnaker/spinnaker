package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.model.Moniker
import de.danielbechler.diff.inclusion.Inclusion
import de.danielbechler.diff.introspection.ObjectDiffProperty

data class SecurityGroup(
  val moniker: Moniker,
  val location: Location,
  val vpcName: String?,
  @get:ObjectDiffProperty(inclusion = Inclusion.EXCLUDED)
  val description: String?,
  val inboundRules: Set<SecurityGroupRule> = emptySet()
) {

  data class Location(
    val accountName: String,
    val region: String
  )
}
