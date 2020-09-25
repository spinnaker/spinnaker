package com.netflix.spinnaker.keel.schema

import com.fasterxml.jackson.annotation.JsonValue
import java.util.SortedMap
import java.util.SortedSet

interface Schema {
  val description: String?
}

sealed class TypedProperty(
  val type: String
) : Schema

data class RootSchema(
  val `$id`: String,
  val title: String?,
  val description: String?,
  val properties: Map<String, Schema>,
  val required: SortedSet<String>,
  val allOf: List<ConditionalSubschema>? = null,
  val `$defs`: SortedMap<String, Schema>
) {
  @Suppress("unused", "PropertyName")
  val `$schema`: String = "https://json-schema.org/draft/2019-09/schema"
  val type: String = "object"
}

data class ObjectSchema(
  val title: String?,
  override val description: String?,
  val properties: Map<String, Schema>,
  val required: SortedSet<String>,
  val allOf: List<ConditionalSubschema>? = null,
  val additionalProperties: Boolean? = null
) : TypedProperty("object")

object NullSchema : TypedProperty("null") {
  override val description: String? = null
}

data class BooleanSchema(override val description: String?) : TypedProperty("boolean")

data class IntegerSchema(override val description: String?) : TypedProperty("integer")

data class NumberSchema(override val description: String?) : TypedProperty("number")

object DurationSchema : TypedProperty("string") {
  override val description = "ISO 8601 duration"

  @Suppress("MayBeConstant", "unused") // doesn't serialize if declared as const
  // see https://rgxdb.com/r/MD2234J
  val pattern: String = """^(-?)P(?=\d|T\d)(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)([DW]))?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?$"""
}

data class AnySchema(override val description: String?) : TypedProperty("object") {
  @Suppress("MayBeConstant") // doesn't serialize if declared as const
  val additionalProperties: Boolean = true
}

data class ArraySchema(
  override val description: String?,
  val items: Schema,
  val uniqueItems: Boolean? = null,
  val minItems: Int? = null
) : TypedProperty("array")

data class MapSchema(
  override val description: String?,
  val additionalProperties: Either<Schema, Boolean>
) : TypedProperty("object")

data class StringSchema(
  override val description: String?,
  val format: String? = null,
  val pattern: String? = null
) : TypedProperty("string")

data class EnumSchema(
  override val description: String?,
  val enum: List<String>
) : Schema

data class ConstSchema(
  override val description: String?,
  val const: String
) : Schema

data class Reference(
  val `$ref`: String
) : Schema {
  override val description: String? = null
}

data class OneOf(
  override val description: String?,
  val oneOf: Set<Schema>
) : Schema

data class ConditionalSubschema(
  val `if`: Condition,
  val then: Subschema
)

data class Condition(
  val properties: Map<String, ConstSchema>
)

data class Subschema(
  val properties: Map<String, Schema>,
  val required: SortedSet<String> = emptySet<String>().toSortedSet()
)

/**
 * Yes, I really had to implement an either monad to get this all to work.
 */
sealed class Either<L, R> {
  data class Left<L, R>(@JsonValue val value: L) : Either<L, R>()
  data class Right<L, R>(@JsonValue val value: R) : Either<L, R>()
}
