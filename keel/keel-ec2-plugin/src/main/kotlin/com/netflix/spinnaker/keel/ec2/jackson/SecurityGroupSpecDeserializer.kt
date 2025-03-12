package com.netflix.spinnaker.keel.ec2.jackson

import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.InjectableValues.Std
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.StdNodeBasedDeserializer
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupSpec
import com.netflix.spinnaker.keel.core.name
import org.springframework.boot.jackson.JsonComponent

@JsonComponent
class SecurityGroupSpecDeserializer : StdNodeBasedDeserializer<SecurityGroupSpec>(SecurityGroupSpec::class.java) {
  override fun convert(root: JsonNode, context: DeserializationContext) =
    with(context.parser.codec as ObjectMapper) {
      val moniker: Moniker = convertValue(root.path("moniker"))
      copy().run {
        val locations: SimpleLocations =
          convertValue(root.path("locations")) ?: context.findInjectableValue("locations")
        injectableValues = Std(mapOf("name" to moniker.name, "locations" to locations))
        SecurityGroupSpec(
          moniker = moniker,
          locations = locations,
          description = root.get("description")?.textValue(),
          inboundRules = root.get("inboundRules")?.let { convertValue(it) } ?: emptySet(),
          overrides = root.get("overrides")?.let { convertValue(it) } ?: emptyMap()
        )
      }
    }
}
