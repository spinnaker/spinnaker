package com.netflix.spinnaker.keel.ec2.jackson

import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.ec2.CidrRule
import com.netflix.spinnaker.keel.api.ec2.CrossAccountReferenceRule
import com.netflix.spinnaker.keel.api.ec2.PrefixListRule
import com.netflix.spinnaker.keel.api.ec2.ReferenceRule
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.jackson.JsonComponent

@JsonComponent
@ConditionalOnMissingBean(name = ["securityGroupRuleDeserializer"])
class DefaultSecurityGroupRuleDeserializer : SecurityGroupRuleDeserializer() {
  override fun identifySubType(
    root: JsonNode,
    context: DeserializationContext,
    fieldNames: Collection<String>
  ): Class<out SecurityGroupRule> =
    when {
      "blockRange" in fieldNames -> CidrRule::class.java
      "prefixListId" in fieldNames -> PrefixListRule::class.java
      "account" in fieldNames -> {
        val account = root.get("account").textValue()
        val locations : SimpleLocations = context.findInjectableValue("locations")
        if (locations.account != account) {
          CrossAccountReferenceRule::class.java
        } else {
          ReferenceRule::class.java
        }
      }
      else -> ReferenceRule::class.java
    }
}
