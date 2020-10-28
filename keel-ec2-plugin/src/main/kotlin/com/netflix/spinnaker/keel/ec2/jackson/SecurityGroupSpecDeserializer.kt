package com.netflix.spinnaker.keel.ec2.jackson

import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.InjectableValues.Std
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.StdNodeBasedDeserializer
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupSpec
import com.netflix.spinnaker.keel.core.name

class SecurityGroupSpecDeserializer : StdNodeBasedDeserializer<SecurityGroupSpec>(SecurityGroupSpec::class.java) {
  override fun convert(root: JsonNode, context: DeserializationContext) =
    with(context.parser.codec as ObjectMapper) {
      val moniker: Moniker = convertValue(root.path("moniker"))
      SecurityGroupSpec(
        moniker = moniker,
        locations = convertValue(root.path("locations"))
          ?: context.findInjectableValue("locations"),
        description = root.get("description")?.textValue(),
        inboundRules = copy().run {
          injectableValues = Std(mapOf("name" to moniker.name))
          root.get("inboundRules")?.let { convertValue(it) } ?: emptySet()
        },
        overrides = root.get("overrides")?.let { convertValue(it) } ?: emptyMap()
      )
    }

  private inline fun <reified T> DeserializationContext.findInjectableValue(valueId: String) =
    (parser.codec as ObjectMapper).let { mapper ->
      mapper.injectableValues.findInjectableValue(valueId, this, null, null) as T
    }
}
