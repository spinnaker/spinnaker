package com.netflix.spinnaker.keel.serialization

import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdNodeBasedDeserializer
import com.fasterxml.jackson.databind.introspect.AnnotatedClassResolver
import com.fasterxml.jackson.databind.type.TypeFactory
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.SubmittedResource

class SubmittedResourceDeserializer : StdNodeBasedDeserializer<SubmittedResource<*>>(SubmittedResource::class.java) {

  override fun convert(root: JsonNode, context: DeserializationContext): SubmittedResource<*> {
    with(context) {
      try {
        val apiVersion = root.path("apiVersion").textValue()
        val kind = root.path("kind").textValue()
        val specType = resolveResourceSpecType(apiVersion, kind)
        val metadata = mapper.convertValue<Map<String, Any?>?>(root.path("metadata")) ?: emptyMap()
        val spec = mapper.convertValue(root.path("spec"), specType) as ResourceSpec

        return SubmittedResource(metadata, apiVersion, kind, spec)
      } catch (e: Exception) {
        throw instantiationException<SubmittedResource<*>>(e)
      }
    }
  }

  private fun DeserializationContext.resolveResourceSpecType(apiVersion: String, kind: String): Class<*> {
    val specBaseType = typeFactory.constructType<ResourceSpec>()

    // Yeah, this is the Jackson API for retrieving the registered sub-types of a class. 😬😱🤐😵
    val specSubTypes = mapper
      .subtypeResolver
      .collectAndResolveSubtypesByTypeId(
        config,
        AnnotatedClassResolver.resolve(config, specBaseType, config)
      )

    return "$apiVersion/$kind".let { typeId ->
      specSubTypes
        .find { it.name == typeId }
        ?.type
        ?: throw invalidTypeIdException(specBaseType, typeId, "")
    }
  }
}

private inline fun <reified T> TypeFactory.constructType() = constructType(T::class.java)
