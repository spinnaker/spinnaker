package com.netflix.spinnaker.keel.serialization

import tools.jackson.databind.BeanProperty
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.InjectableValues
import tools.jackson.databind.JavaType
import tools.jackson.databind.JsonNode
import tools.jackson.databind.deser.std.StdNodeBasedDeserializer
import tools.jackson.core.JacksonException
import tools.jackson.core.type.TypeReference
import com.netflix.spinnaker.keel.api.Constraint
import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.postdeploy.PostDeployAction
import com.netflix.spinnaker.keel.api.toSimpleLocations
import com.netflix.spinnaker.keel.core.api.SubmittedEnvironment
import com.netflix.spinnaker.keel.core.api.SubmittedResource

/**
 * Deserializer that allows us to propagate values such as [SubmittedEnvironment.locations] to all
 * resources in the environment without having to make the corresponding properties in the resource
 * specs nullable and continually have to look up the environment.
 */
class SubmittedEnvironmentDeserializer : StdNodeBasedDeserializer<SubmittedEnvironment>(SubmittedEnvironment::class.java) {
  override fun convert(root: JsonNode, context: DeserializationContext): SubmittedEnvironment {
    val typeFactory = context.typeFactory
    // Jackson 3 throws when reading a String value from a missing/non-textual node,
    // while Jackson 2 returned null. Preserve the null so a missing name still fails
    // via SubmittedEnvironment construction (mapped to INVALID_VALUE downstream).
    val nameNode = root.path("name")
    val name = if (nameNode.isTextual) nameNode.stringValue() else null
    val constraints: Set<Constraint> = context.convert(
      root,
      "constraints",
      typeFactory.constructType(object : TypeReference<Set<Constraint>>() {})
    ) ?: emptySet()
    val verifyWith: List<Verification> = context.convert(
      root,
      "verifyWith",
      typeFactory.constructType(object : TypeReference<List<Verification>>() {})
    ) ?: emptyList()
    val notifications: Set<NotificationConfig> = context.convert(
      root,
      "notifications",
      typeFactory.constructType(object : TypeReference<Set<NotificationConfig>>() {})
    ) ?: emptySet()
    val postDeploy: List<PostDeployAction> = context.convert(
      root,
      "postDeploy",
      typeFactory.constructType(object : TypeReference<List<PostDeployAction>>() {})
    ) ?: emptyList()
    val locations: SubnetAwareLocations? = context.convert(root, "locations", typeFactory.constructType(SubnetAwareLocations::class.java))
    context.setAttribute("locations", locations)
    val resources: Set<SubmittedResource<*>> = context.convert(
      root,
      "resources",
      typeFactory.constructType(object : TypeReference<Set<SubmittedResource<*>>>() {})
    ) ?: emptySet()
    return try {
      // name!! restores the Jackson 2 behavior where a missing name produced null and failed
      // here; the resulting exception is mapped to INVALID_VALUE downstream.
      SubmittedEnvironment(name!!, resources, constraints, verifyWith, notifications, postDeploy, locations)
    } catch (e: Exception) {
      throw context.instantiationException<SubmittedEnvironment>(e)
    }
  }

  private fun <T> DeserializationContext.convert(root: JsonNode, path: String, type: JavaType): T? =
    try {
      readTreeAsValue(root.path(path), type)
    } catch (e: JacksonException) {
      // Jackson 3 databind failures are unchecked JacksonExceptions (Jackson 2 raised
      // IOExceptions surfaced here as IllegalArgumentException); re-wrap with the
      // property path so error details keep the full path.
      throw JacksonException.wrapWithPath(e, root, path)
    } catch (e: IllegalArgumentException) {
      throw JacksonException.wrapWithPath(e, root, path)
    }
}
