package com.netflix.spinnaker.keel.ec2.jackson

import com.fasterxml.jackson.databind.BeanProperty
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.InjectableValues.Std
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyMetadata
import com.fasterxml.jackson.databind.PropertyName
import com.fasterxml.jackson.databind.deser.std.StdNodeBasedDeserializer
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupSpec
import com.netflix.spinnaker.keel.core.name
import org.springframework.boot.jackson.JsonComponent

@JsonComponent
class SecurityGroupSpecDeserializer : StdNodeBasedDeserializer<SecurityGroupSpec>(SecurityGroupSpec::class.java) {
  override fun convert(root: JsonNode, context: DeserializationContext) =
    with(context.parser.codec as ObjectMapper) {
      val moniker: Moniker = convertValue(root.path("moniker"))
      copy().run {
        injectableValues = Std(mapOf("name" to moniker.name))
        SecurityGroupSpec(
          moniker = moniker,
          locations = convertValue(root.path("locations"))
            ?: context.findInjectableValue("locations"),
          description = root.get("description")?.textValue(),
          inboundRules = root.get("inboundRules")?.let { convertValue(it) } ?: emptySet(),
          overrides = root.get("overrides")?.let { convertValue(it) } ?: emptyMap()
        )
      }
    }

  private inline fun <reified T> DeserializationContext.findInjectableValue(
    valueId: String,
    propertyName: String = valueId
  ) =
    (parser.codec as ObjectMapper).let { mapper ->
      mapper.injectableValues.findInjectableValue(
        valueId,
        this,
        BeanProperty.Std(
          PropertyName.construct(propertyName),
          constructType(T::class.java),
          PropertyName.construct(propertyName),
          null,
          PropertyMetadata.STD_REQUIRED_OR_OPTIONAL
        ),
        null
      ) as T
    }
}
