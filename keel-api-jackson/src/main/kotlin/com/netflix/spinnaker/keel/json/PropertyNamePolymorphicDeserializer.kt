package com.netflix.spinnaker.keel.json

import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdNodeBasedDeserializer

/**
 * Base class for deserializing a polymorphic type by looking at the fields present in the JSON to
 * identify the sub-type. This means no type field is necessary in the JSON.
 *
 * Extend this class and implement [identifySubType] then put
 * `@JsonDeserialize(using = MyDeserializer::class)` on the base class and
 * `@JsonDeserialize(using = JsonDeserializer.None::class)` on _all_ the sub-types.
 */
abstract class PropertyNamePolymorphicDeserializer<T>(clazz: Class<T>) : StdNodeBasedDeserializer<T>(clazz) {

  override fun convert(root: JsonNode, context: DeserializationContext): T {
    val subType = identifySubType(root.fieldNames().asSequence().toList())
    return context.parser.codec.treeToValue(root, subType)
  }

  protected abstract fun identifySubType(fieldNames: Collection<String>): Class<out T>
}
