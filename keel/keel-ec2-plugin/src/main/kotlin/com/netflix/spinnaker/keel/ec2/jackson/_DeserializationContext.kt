package com.netflix.spinnaker.keel.ec2.jackson

import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.toSimpleLocations
import tools.jackson.databind.DeserializationContext

/**
 * Jackson 3 removed `ObjectMapper.injectableValues` access and `DeserializationContext`
 * single-arg lookup, so this goes straight to the context attributes (populated by the
 * environment/spec deserializers) and applies the one conversion the locations attribute
 * needs: [SubnetAwareLocations] values requested as [SimpleLocations].
 */
internal inline fun <reified T> DeserializationContext.findInjectableValue(
  valueId: String,
  propertyName: String = valueId
): T {
  val value = getAttribute(valueId) ?: error("No injectable value for '$valueId'")
  @Suppress("UNCHECKED_CAST")
  return when {
    value is SubnetAwareLocations && SimpleLocations::class.java.isAssignableFrom(T::class.java) ->
      value.toSimpleLocations() as T
    else -> value as T
  }
}
