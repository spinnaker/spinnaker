package com.netflix.spinnaker.keel.ec2.jackson

import com.netflix.spinnaker.keel.api.ec2.CidrRule
import com.netflix.spinnaker.keel.api.ec2.CrossAccountReferenceRule
import com.netflix.spinnaker.keel.api.ec2.ReferenceRule
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.jackson.JsonComponent

@JsonComponent
//@ConditionalOnMissingBean(SecurityGroupRuleDeserializer::class)
@ConditionalOnProperty("keel.plugins.ec2.defaultSerializers", matchIfMissing = true)
class DefaultSecurityGroupRuleDeserializer : SecurityGroupRuleDeserializer() {
  override fun identifySubType(fieldNames: Collection<String>): Class<out SecurityGroupRule> =
    when {
      "blockRange" in fieldNames -> CidrRule::class.java
      "account" in fieldNames -> CrossAccountReferenceRule::class.java
      else -> ReferenceRule::class.java
    }
}