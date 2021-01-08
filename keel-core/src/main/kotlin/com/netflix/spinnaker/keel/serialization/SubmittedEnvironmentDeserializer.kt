package com.netflix.spinnaker.keel.serialization

import com.fasterxml.jackson.databind.BeanProperty
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.InjectableValues
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.StdNodeBasedDeserializer
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.api.Constraint
import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.toSimpleLocations
import com.netflix.spinnaker.keel.core.api.SubmittedEnvironment
import com.netflix.spinnaker.keel.core.api.SubmittedResource

/**
 * Deserializer that allows us to propagate values such as [SubmittedEnvironment.locations] to all
 * resources in the environment without having to make the corresponding properties in the resource
 * specs nullable and continually have to look up the environment.
 */
class SubmittedEnvironmentDeserializer : StdNodeBasedDeserializer<SubmittedEnvironment>(SubmittedEnvironment::class.java) {
  override fun convert(root: JsonNode, context: DeserializationContext): SubmittedEnvironment =
    with(context.mapper) {
      val name = root.path("name").textValue()
      val constraints: Set<Constraint> = convert(root, "constraints") ?: emptySet()
      val verifyWith: List<Verification> = convert(root, "verifyWith") ?: emptyList()
      val notifications: Set<NotificationConfig> = convert(root, "notifications") ?: emptySet()
      val locations: SubnetAwareLocations? = convert(root, "locations")
      val resources: Set<SubmittedResource<*>> = copy().run {
        injectableValues = InjectableLocations(locations)
        convert(root, "resources") ?: emptySet()
      }
      try {
        SubmittedEnvironment(name, resources, constraints, verifyWith, notifications, locations)
      } catch (e: Exception) {
        throw context.instantiationException<SubmittedEnvironment>(e)
      }
    }

  private inline fun <reified T : Any> ObjectMapper.convert(root: JsonNode, path: String): T? =
    try {
      convertValue(root.path(path))
    } catch (e: IllegalArgumentException) {
      throw JsonMappingException.wrapWithPath(e, root, path)
    }
}

private class InjectableLocations(
  value: SubnetAwareLocations?
) : InjectableValues.Std(mapOf("locations" to value)) {
  override fun findInjectableValue(
    valueId: Any,
    context: DeserializationContext,
    forProperty: BeanProperty,
    beanInstance: Any?
  ): Any? {
    val value = super.findInjectableValue(valueId, context, forProperty, beanInstance) as? SubnetAwareLocations
    return when {
      value == null -> null
      forProperty.type.isTypeOrSubTypeOf(SimpleLocations::class.java) -> value.toSimpleLocations()
      else -> value
    }
  }
}
