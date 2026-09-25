package com.netflix.spinnaker.keel.ec2.jackson

import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.deser.std.StdNodeBasedDeserializer
import tools.jackson.core.type.TypeReference
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupOverride
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupSpec
import com.netflix.spinnaker.keel.core.name
import org.springframework.boot.jackson.JacksonComponent


@JacksonComponent
class SecurityGroupSpecDeserializer : StdNodeBasedDeserializer<SecurityGroupSpec>(SecurityGroupSpec::class.java) {
  override fun convert(root: JsonNode, context: DeserializationContext): SecurityGroupSpec {
    val moniker: Moniker = context.readTreeAsValue<Moniker>(root.path("moniker"), Moniker::class.java)
    val locations: SimpleLocations = root.get("locations")?.let {
      context.readTreeAsValue<SimpleLocations>(it, SimpleLocations::class.java)
    } ?: context.findInjectableValue("locations")

    context.setAttribute("name", moniker.name)
    context.setAttribute("locations", locations)

    val inboundRules: Set<SecurityGroupRule> = root.get("inboundRules")?.let {
      context.readTreeAsValue<Set<SecurityGroupRule>>(it, context.typeFactory.constructType(object : TypeReference<Set<SecurityGroupRule>>() {}))
    } ?: emptySet()
    val overrides: Map<String, SecurityGroupOverride> = root.get("overrides")?.let {
      context.readTreeAsValue<Map<String, SecurityGroupOverride>>(it, context.typeFactory.constructType(object : TypeReference<Map<String, SecurityGroupOverride>>() {}))
    } ?: emptyMap()

    return SecurityGroupSpec(
      moniker = moniker,
      locations = locations,
      description = root.get("description")?.textValue(),
      inboundRules = inboundRules,
      overrides = overrides
    )
  }
}
