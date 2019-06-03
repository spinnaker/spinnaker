package com.netflix.spinnaker.keel.api.ec2.jackson

import com.netflix.spinnaker.keel.api.ec2.securityGroup.CidrRule
import com.netflix.spinnaker.keel.api.ec2.securityGroup.CrossAccountReferenceRule
import com.netflix.spinnaker.keel.api.ec2.securityGroup.ReferenceRule
import com.netflix.spinnaker.keel.api.ec2.securityGroup.SecurityGroupRule
import com.netflix.spinnaker.keel.api.ec2.securityGroup.SelfReferenceRule
import com.netflix.spinnaker.keel.serialization.PropertyNamePolymorphicDeserializer

internal class SecurityGroupRuleDeserializer :
  PropertyNamePolymorphicDeserializer<SecurityGroupRule>(SecurityGroupRule::class.java) {
  override fun identifySubType(fieldNames: Collection<String>): Class<out SecurityGroupRule> =
    when {
      "blockRange" in fieldNames -> CidrRule::class.java
      "account" in fieldNames -> CrossAccountReferenceRule::class.java
      "name" in fieldNames -> ReferenceRule::class.java
      else -> SelfReferenceRule::class.java
    }
}
