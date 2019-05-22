package com.netflix.spinnaker.keel.serialization

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * Base class for deserializing a polymorphic type by looking at the fields present in the JSON to
 * identify the sub-type. This means no type field is necessary in the JSON.
 *
 * Extend this class and implement [identifySubType] then put
 * `@JsonDeserialize(using = MyDeserializer::class)` on the base class and
 * `@JsonDeserialize(using = JsonDeserializer.None::class)` on _all_ the sub-types.
 */
abstract class PropertyNamePolymorphicDeserializer<T>(clazz: Class<T>) : StdDeserializer<T>(clazz) {

  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): T {
    val obj = p.codec.readTree<ObjectNode>(p)
    val fieldNames = obj.fieldNames().asSequence().toSet()
    val subType: Class<out T> = identifySubType(fieldNames)
    return p.codec.treeToValue(obj, subType)
  }

  protected abstract fun identifySubType(fieldNames: Collection<String>): Class<out T>
}
