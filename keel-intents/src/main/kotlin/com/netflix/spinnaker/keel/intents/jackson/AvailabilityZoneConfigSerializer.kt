package com.netflix.spinnaker.keel.intents.jackson

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.TextNode
import com.netflix.spinnaker.keel.intents.AvailabilityZoneConfig
import com.netflix.spinnaker.keel.intents.AvailabilityZoneConfig.Automatic
import com.netflix.spinnaker.keel.intents.AvailabilityZoneConfig.Manual

class AvailabilityZoneConfigSerializer : JsonSerializer<AvailabilityZoneConfig>() {
  override fun serialize(
    value: AvailabilityZoneConfig,
    gen: JsonGenerator,
    serializers: SerializerProvider
  ) {
    when (value) {
      is Automatic -> gen.writeString(Automatic.javaClass.simpleName.toLowerCase())
      is Manual -> value.availabilityZones.apply {
        gen.writeStartArray()
        forEach {
          gen.writeString(it)
        }
        gen.writeEndArray()
      }
    }
  }
}

class AvailabilityZoneConfigDeserializer : JsonDeserializer<AvailabilityZoneConfig>() {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): AvailabilityZoneConfig {
    val tree: TreeNode = p.readValueAsTree()
    return when (tree) {
      is TextNode -> Automatic // TODO: pretty crude assumption here
      is ArrayNode -> Manual(tree.map { it.textValue() }.toSet())
      else -> throw InvalidFormatException(
        p,
        "Expected text or array but found ${tree.javaClass.simpleName}",
        tree,
        AvailabilityZoneConfig::class.java
      )
    }
  }

}
