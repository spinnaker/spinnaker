package com.netflix.spinnaker.keel.jsonschema

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.introspect.AnnotatedClass
import com.fasterxml.jackson.databind.introspect.AnnotatedClassResolver
import com.fasterxml.jackson.databind.jsontype.NamedType
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.fasterxml.jackson.module.kotlin.convertValue
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.temporal.Temporal
import java.time.temporal.TemporalAmount
import java.util.UUID
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.full.withNullability
import kotlin.reflect.jvm.jvmErasure
import org.slf4j.LoggerFactory

class JsonSchemaGenerator(
  private val mapper: ObjectMapper,
  private val config: SchemaGeneratorConfiguration = SchemaGeneratorConfiguration()
) {
  private val handlers = listOf(
    NullableTypeHandler,
    BooleanTypeHandler,
    NumberTypeHandler,
    EnumTypeHandler,
    StringTypeHandler,
    ArrayTypeHandler,
    AnyTypeHandler,
    AbstractTypeHandler,
    SealedTypeHandler,
    ObjectTypeHandler
  )

  inline fun <reified T> generate(id: String): JsonNode = generate(T::class, id)

  fun generate(type: KClass<*>, id: String): JsonNode {
    val definitions = mutableSetOf<KClass<*>>()

    val context = object : HandlerContext {
      override fun build(type: KType): Map<String, Any?> {
        val handler = handlers.selectHandler(type)
        return if (handler.deferBuild) {
          defer(type.jvmErasure)
          mapOf("\$ref" to "#/\$defs/${type.jvmErasure.simpleName}")
        } else {
          handler.buildSchema(type, this)
        }
      }

      override val config = this@JsonSchemaGenerator.config
      override val mapper = this@JsonSchemaGenerator.mapper

      override fun defer(type: KClass<out Any>) {
        val clash = definitions.find { it.simpleName == type.simpleName && it != type }
        if (clash != null) {
          log.warn("Clashing class names detected in schema: {}, {}", clash, type)
        }
        definitions.add(type)
      }
    }

    return mutableMapOf<String, Any?>()
      .apply {
        put("\$id", id)
        put("\$root", "http://json-schema.org/draft-07/schema#")
        put("title", type.simpleName)
        putAll(handlers.applyHandlers(type.starProjectedType, context)) // TODO: assumes no generic types
        if (definitions.isNotEmpty()) {
          put(
            "\$defs",
            definitions.buildDefinitions(context)
          )
        }
      }
      .let { mapper.convertValue(it) }
  }

  private fun List<TypeHandler>.applyHandlers(
    type: KType,
    context: HandlerContext
  ): Map<String, Any?> =
    selectHandler(type)
      .buildSchema(type, context)

  private fun List<TypeHandler>.selectHandler(type: KType) =
    first { it.handles(type) }

  private fun Set<KClass<*>>.buildDefinitions(context: HandlerContext): Map<String, Any?> {
    val destination = mutableMapOf<String, Any?>().toSortedMap()
    val processed = mutableSetOf<KClass<*>>()
    do {
      val delta = this - processed
      delta.forEach {
        if (!processed.contains(it)) {
          destination += checkNotNull(it.simpleName) to handlers.applyHandlers(it.starProjectedType, context) // TODO: assumes no generic types
          processed += it
        }
      }
    } while (delta.isNotEmpty())
    return destination
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}

data class SchemaGeneratorConfiguration(
  val nullableTypesAsOneOf: Boolean = false
)

private interface HandlerContext {
  val config: SchemaGeneratorConfiguration
  val mapper: ObjectMapper
  fun build(type: KType): Map<String, Any?>
  fun defer(type: KClass<*>)
}

private sealed class TypeHandler {
  abstract fun handles(type: KType): Boolean
  abstract fun buildSchema(type: KType, context: HandlerContext): Map<String, Any?>
  open val deferBuild: Boolean = false
}

private object NullableTypeHandler : TypeHandler() {
  override fun handles(type: KType) = type.isMarkedNullable

  override fun buildSchema(type: KType, context: HandlerContext) =
    if (context.config.nullableTypesAsOneOf) {
      mapOf(
        "oneOf" to listOf(
          mapOf("type" to "null"),
          context.build(type.withNullability(false))
        )
      )
    } else {
      context.build(type.withNullability(false))
    }
}

private object StringTypeHandler : TypeHandler() {
  private val supportedTypes = setOf(
    CharSequence::class,
    Temporal::class,
    TemporalAmount::class,
    UUID::class
  )

  private val formats = mapOf(
    Instant::class to "date-time",
    ZonedDateTime::class to "date-time",
    OffsetDateTime::class to "date-time",
    LocalDateTime::class to "date-time",
    LocalDate::class to "date",
    LocalTime::class to "time",
    Duration::class to "duration",
    UUID::class to "uuid"
  )

  override fun handles(type: KType) =
    type.serializesToString() || supportedTypes.any {
      type.jvmErasure.isSubclassOf(it)
    }

  private fun KType.serializesToString() =
    jvmErasure.findAnnotation<JsonSerialize>()?.using == ToStringSerializer::class

  override fun buildSchema(type: KType, context: HandlerContext) =
    mapOf(
      "type" to "string",
      "format" to formats[type.jvmErasure]
    )
      .filterValues { it != null }
}

private object BooleanTypeHandler : TypeHandler() {
  override fun handles(type: KType) =
    type.jvmErasure == Boolean::class

  override fun buildSchema(type: KType, context: HandlerContext) =
    mapOf("type" to "boolean")
}

private object NumberTypeHandler : TypeHandler() {
  override fun handles(type: KType) =
    Number::class.isSuperclassOf(type.jvmErasure)

  override fun buildSchema(type: KType, context: HandlerContext) =
    mapOf("type" to "number")
}

private object EnumTypeHandler : TypeHandler() {
  override fun handles(type: KType) = type.jvmErasure.java.isEnum

  override fun buildSchema(type: KType, context: HandlerContext) =
    mapOf("enum" to type.jvmErasure.java.enumConstants)
}

private object ArrayTypeHandler : TypeHandler() {
  override fun handles(type: KType) =
    type.isArray || type.isIterable

  override fun buildSchema(type: KType, context: HandlerContext) =
    mutableMapOf<String, Any?>("type" to "array")
      .apply {
        val elementType = type.arguments.first().type
        if (elementType != null) {
          put("items", context.build(elementType))
        }
        if (type.isSet) {
          put("uniqueItems", true)
        }
      }

  private val KType.isArray: Boolean
    get() = jvmErasure.java.isArray

  private val KType.isIterable: Boolean
    get() = jvmErasure.isSubclassOf(Iterable::class)

  private val KType.isSet: Boolean
    get() = jvmErasure.isSubclassOf(Set::class)
}

private object ObjectTypeHandler : TypeHandler() {
  override fun handles(type: KType) = true

  override fun buildSchema(type: KType, context: HandlerContext): Map<String, Any?> {
    val constructor = type.preferredConstructor
    val typeId = context.resolveTypeId(type)
    val typeIdProperty: Map<String, Any?> = typeId
      ?.let { (name, value) ->
        mapOf(name to mapOf("enum" to listOf(value)))
      }
      ?: emptyMap()
    return mapOf(
      "type" to "object",
      "properties" to typeIdProperty + context.buildProperties(constructor),
      "required" to buildRequired(constructor).let {
        if (typeId == null) it
        else it + typeId.first
      }
    )
  }

  override val deferBuild = true

  private fun HandlerContext.resolveTypeId(type: KType): Pair<String, String>? {
    val superclass = type
      .jvmErasure
      .allSuperclasses
      .firstOrNull { it.hasAnnotation<JsonTypeInfo>() }
    if (superclass != null) {
      val typeInfo = superclass.findAnnotation<JsonTypeInfo>()
      if (typeInfo?.include == As.PROPERTY) {
        val namedType = mapper.resolveSubtypes(superclass)
          .find { it.type == type.jvmErasure.java }
        val typeIdProperty = typeInfo.property.ifEmpty { null } ?: typeInfo.use.defaultPropertyName
        val typeIdValue = checkNotNull(namedType).name
        return typeIdProperty to typeIdValue
      }
    }
    return null
  }

  // TODO: can drop this in favor of experimental stdlib API method
  private inline fun <reified T : Annotation> KAnnotatedElement.hasAnnotation() =
    annotations.any { it is T }

  private val KType.preferredConstructor: KFunction<*>?
    get() = jvmErasure.run {
      constructors
        .find { it.hasAnnotation<JsonCreator>() }
        ?: primaryConstructor
    }

  private fun HandlerContext.buildProperties(constructor: KFunction<*>?): Map<String, Any?> =
    constructor?.parameters?.associate { property ->
      checkNotNull(property.name) to build(property.type)
    } ?: emptyMap()

  private fun buildRequired(constructor: KFunction<*>?): List<String> =
    constructor
      ?.parameters
      ?.filterNot { it.type.isMarkedNullable || it.isOptional }
      ?.map { checkNotNull(it.name) }
      ?: emptyList()
}

private object AbstractTypeHandler : TypeHandler() {
  override fun handles(type: KType) = type.jvmErasure.isAbstract

  override fun buildSchema(type: KType, context: HandlerContext) =
    mapOf(
      "oneOf" to context.mapper.resolveSubtypes(type.jvmErasure)
        .map {
          context.defer(it.type.kotlin)
          mapOf("\$ref" to "#/\$defs/${it.type.simpleName}")
        }
    )

  override val deferBuild = true
}

private object SealedTypeHandler : TypeHandler() {
  override fun handles(type: KType) = type.jvmErasure.isSealed

  override fun buildSchema(type: KType, context: HandlerContext) =
    mapOf(
      "oneOf" to type.jvmErasure.sealedSubclasses
        .map {
          context.defer(it)
          mapOf("\$ref" to "#/\$defs/${it.simpleName}")
        }
    )

  override val deferBuild = true
}

private object AnyTypeHandler : TypeHandler() {
  override fun handles(type: KType) =
    type.jvmErasure.let {
      it == Any::class || it.isSubclassOf(Map::class)
    }

  override fun buildSchema(type: KType, context: HandlerContext): Map<String, Any?> =
    mapOf("type" to "object")
}

private fun ObjectMapper.resolveSubtypes(type: KClass<*>): Collection<NamedType> =
  subtypeResolver
    .collectAndResolveSubtypesByTypeId(
      deserializationConfig,
      resolveAnnotatedClassFor(type)
    )

private fun ObjectMapper.resolveAnnotatedClassFor(type: KClass<*>): AnnotatedClass =
  AnnotatedClassResolver.resolve(
    deserializationConfig,
    typeFactory.constructType(type.java),
    deserializationConfig
  )
