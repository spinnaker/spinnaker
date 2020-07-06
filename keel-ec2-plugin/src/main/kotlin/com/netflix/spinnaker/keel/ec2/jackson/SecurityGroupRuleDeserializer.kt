package com.netflix.spinnaker.keel.ec2.jackson

import com.netflix.spinnaker.keel.api.ec2.CidrRule
import com.netflix.spinnaker.keel.api.ec2.CrossAccountReferenceRule
import com.netflix.spinnaker.keel.api.ec2.ReferenceRule
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule
import com.netflix.spinnaker.keel.jackson.PropertyNamePolymorphicDeserializer

internal class SecurityGroupRuleDeserializer :
  PropertyNamePolymorphicDeserializer<SecurityGroupRule>(SecurityGroupRule::class.java) {
  override fun identifySubType(fieldNames: Collection<String>): Class<out SecurityGroupRule> =
    when {
      "blockRange" in fieldNames -> CidrRule::class.java
      "account" in fieldNames -> CrossAccountReferenceRule::class.java
      else -> ReferenceRule::class.java
    }
}
