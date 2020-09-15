package com.netflix.spinnaker.keel.schema

import com.fasterxml.jackson.annotation.JsonCreator
import com.netflix.spinnaker.keel.api.schema.Description
import com.netflix.spinnaker.keel.api.schema.Discriminator
import com.netflix.spinnaker.keel.api.schema.Factory
import com.netflix.spinnaker.keel.api.schema.Literal
import com.netflix.spinnaker.keel.api.schema.Optional
import com.netflix.spinnaker.keel.api.support.ExtensionRegistry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.util.SortedSet
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection.Companion.invariant
import kotlin.reflect.full.createType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.full.withNullability
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

class Generator(
  private val extensionRegistry: ExtensionRegistry,
  private val options: Options = Options(),
  private val schemaCustomizers: Collection<SchemaCustomizer> = emptyList()
) {
  data class Options(
    /**
     * If `true`, Kotlin nullable properties get represented as one of null / whatever the non-null
     * type is. If `false` nullable properties are treated as optional (because omitting them in the
     * JSON maps to `null` when Jackson parses it).
     */
    val nullableAsOneOf: Boolean = false
  )

  /**
   * Contains linked schemas that we find along the way.
   */
  private data class Context(
    val definitions: MutableMap<String, Schema> = mutableMapOf()
  )

  /**
   * Generate a schema for [type].
   *
   * @return a full schema document including `$defs` of all linked schemas required to fully
   * specify [type].
   */
  fun <TYPE : Any> generateSchema(type: KClass<TYPE>): RootSchema {
    val context = Context()

    val schema = context.buildSchema(type) as ObjectSchema

    return RootSchema(
      `$id` = "http://keel.spinnaker.io/${type.simpleName}",
      title = checkNotNull(type.simpleName),
      description = type.description,
      properties = schema.properties,
      required = schema.required,
      discriminator = schema.discriminator,
      `$defs` = context.definitions.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    )
  }

  /**
   * Build a schema for [type]. Any referenced schemas not already defined will be added to
   * the [Context] as they are discovered.
   */
  private fun Context.buildSchema(type: KClass<*>, discriminator: Pair<KProperty<String>, String>? = null): Schema =
    when {
      // If we have a schema customizer for this type, just use that
      schemaCustomizers.any { it.supports(type) } -> schemaCustomizers
        .first { it.supports(type) }
        .buildSchema()
      type.isSingleton -> buildSchemaForKotlinSingleton(type)
      type.isSealed -> buildSchemaForSealedClass(type)
      extensionRegistry.baseTypes().contains(type.java) -> buildSchemaForTypeHierarchy(type)
      type.typeParameters.isNotEmpty() -> buildSchemaForGenericTypeHierarchy(type, discriminator)
      // Otherwise this is just a regular object schema
      else -> buildSchemaForRegularClass(type, discriminator)
    }

  /**
   * A regular class is just represented as an [ObjectSchema].
   *
   * @param discriminator required if this is a leaf type of a type hierarchy.
   */
  private fun Context.buildSchemaForRegularClass(
    type: KClass<*>,
    discriminator: Pair<KProperty<String>, String>?
  ) =
    ObjectSchema(
      title = checkNotNull(type.simpleName),
      description = type.description,
      properties = type.candidateProperties.associate {
        checkNotNull(it.name) to buildProperty(owner = type.starProjectedType, parameter = it)
      } + discriminator.toDiscriminatorEnum(),
      required = type.candidateProperties.toRequiredPropertyNames().let {
        if (discriminator != null) {
          (it + discriminator.first.name).toSortedSet(String.CASE_INSENSITIVE_ORDER)
        } else {
          it
        }
      }
    )

  /**
   * Types that are registered base types in [ExtensionRegistry] are treated as [OneOf] the known
   * sub-types.
   */
  private fun Context.buildSchemaForTypeHierarchy(type: KClass<*>) =
    OneOf(
      description = type.description,
      oneOf = extensionRegistry
        .extensionsOf(type.java)
        .map { define(it.value.kotlin, type.discriminatorProperty to it.key) }
        .toSet(),
      discriminator = OneOf.Discriminator(
        propertyName = type.discriminatorProperty.name,
        mapping = extensionRegistry
          .extensionsOf(type.java)
          .mapValues { it.value.kotlin.buildRef().`$ref` }
          .toSortedMap(String.CASE_INSENSITIVE_ORDER)
      )
    )

  /**
   * Base types with a generic parameter are represented as an [ObjectSchema] with the common
   * properties and a variant for every sub-type of the generic upper bound. Each variant will be
   * [AllOf] the base type and the generic properties.
   *
   * @param discriminator required if this is a leaf type of a type hierarchy.
   */
  private fun Context.buildSchemaForGenericTypeHierarchy(
    type: KClass<*>,
    discriminator: Pair<KProperty<String>, String>?
  ): ObjectSchema {
    val invariantTypes = extensionRegistry
      .extensionsOf(type.typeParameters.first().upperBounds.first().jvmErasure.java)
    return ObjectSchema(
      title = checkNotNull(type.simpleName),
      description = type.description,
      properties = type
        .candidateProperties
        .filter {
          // filter out properties of the generic type as they will be specified in extended types
          it.type.classifier !in type.typeParameters
        }
        .associate {
          checkNotNull(it.name) to buildProperty(owner = type.starProjectedType, parameter = it)
        },
      required = type.candidateProperties.toRequiredPropertyNames(),
      discriminator = OneOf.Discriminator(
        propertyName = type.discriminatorProperty.name,
        mapping = invariantTypes
          .mapValues { it.value.kotlin.buildRef().`$ref` }
          .toSortedMap(String.CASE_INSENSITIVE_ORDER)
      )
    )
      .also {
        invariantTypes
          .forEach { (_, subType) ->
            type.createType(
              arguments = listOf(invariant(subType.kotlin.createType()))
            )
              .also { _ ->
                val name = "${subType.simpleName}${type.simpleName}"
                if (!definitions.containsKey(name)) {
                  val genericProperties = type.candidateProperties.filter {
                    it.type.classifier in type.typeParameters
                  }
                  definitions[name] = AllOf(
                    listOf(
                      // reference to the base type
                      Reference("#/${RootSchema::`$defs`.name}/${type.simpleName}"),
                      // any generic properties of the base type with the invariant type applied
                      ObjectSchema(
                        title = null,
                        description = null,
                        properties = genericProperties
                          .associate {
                            checkNotNull(it.name) to buildProperty(
                              owner = type.starProjectedType,
                              parameter = it,
                              type = subType.kotlin.starProjectedType
                            )
                          } + discriminator.toDiscriminatorEnum(),
                        required = genericProperties.toRequiredPropertyNames().let {
                          if (discriminator != null) {
                            (it + discriminator.first.name).toSortedSet(String.CASE_INSENSITIVE_ORDER)
                          } else {
                            it
                          }
                        }
                      )
                    )
                  )
                }
              }
          }
      }
  }

  /**
   * Kotlin singleton objects are represented as a single-value enum.
   */
  private fun buildSchemaForKotlinSingleton(type: KClass<*>) =
    EnumSchema(
      description = type.description,
      enum = listOf(type.findAnnotation<Literal>()?.value ?: checkNotNull(type.simpleName))
    )

  /**
   * The root of a sealed class hierarchy is represented as [OneOf] the sub-type schemas.
   */
  private fun Context.buildSchemaForSealedClass(type: KClass<*>) =
    OneOf(
      description = type.description,
      oneOf = type.sealedSubclasses.map { define(it) }.toSet()
    )

  private fun Pair<KProperty<String>, String>?.toDiscriminatorEnum() =
    if (this != null) {
      mapOf(first.name to EnumSchema(description = null, enum = listOf(second)))
    } else {
      emptyMap()
    }

  /**
   * The properties of as type that we will want to document in it's schema. Unless otherwise
   * specified this means the parameters of its primary constructor.
   */
  private val KClass<*>.candidateProperties: List<KParameter>
    get() = when {
      isAbstract -> emptyList()
      isSingleton -> emptyList()
      else -> preferredConstructor.parameters
    }

  private val KClass<*>.preferredConstructor: KFunction<Any>
    get() = (
      constructors.firstOrNull { it.hasAnnotation<Factory>() }
        ?: constructors.firstOrNull { it.hasAnnotation<JsonCreator>() }
        ?: primaryConstructor
        ?: constructors.firstOrNull()
      ).let {
        checkNotNull(it) {
          "$qualifiedName has no candidate constructor"
        }
      }

  /**
   * The name of the property annotated with `@[Discriminator]`
   */
  private val KClass<*>.discriminatorProperty: KProperty<String>
    get() = checkNotNull(memberProperties.find { it.hasAnnotation<Discriminator>() }) {
      "$simpleName has no property annotated with @Discriminator but is registered as an extension base type"
    } as KProperty<String>

  /**
   * Build the property schema for [parameter].
   *
   * - In the case of a nullable property, this is [OneOf] `null` and the non-null type.
   * - In the case of a string, integer, boolean, or enum this is a [TypedProperty].
   * - In the case of an array-like type this is an [ArraySchema].
   * - In the case of a [Map] this is a [MapSchema].
   * - Otherwise this is is a [Reference] to the schema for the type, which will be added to this
   * [Context] if not already defined.r
   */
  private fun Context.buildProperty(
    owner: KType,
    parameter: KParameter,
    type: KType = parameter.type
  ): Schema =
    buildProperty(
      type,
      owner
        .jvmErasure
        .backingPropertyFor(parameter)
        ?.description
    )

  private fun Context.buildProperty(type: KType, description: String? = null): Schema =
    when {
      type.isMarkedNullable -> if (options.nullableAsOneOf) {
        OneOf(
          description = description,
          oneOf = setOf(NullSchema, buildProperty(
            type = type.withNullability(false),
          ))
        )
      } else {
        buildProperty(
          type = type.withNullability(false),
          description = description
        )
      }
      type.isSingleton -> buildSchema(type.jvmErasure)
      type.isEnum -> EnumSchema(description = description, enum = type.enumNames)
      type.isString -> StringSchema(description = description, format = type.stringFormat)
      type.isBoolean -> BooleanSchema(description = description)
      type.isInteger -> IntegerSchema(description = description)
      type.isNumber -> NumberSchema(description = description)
      type.isArray -> {
        ArraySchema(
          description = description,
          items = buildProperty(type.elementType),
          uniqueItems = if (type.isUniqueItems) true else null
        )
      }
      type.isMap -> {
        MapSchema(
          description = description,
          additionalProperties = buildProperty(type.valueType).let {
            if (it is AnySchema) {
              Either.Right(true)
            } else {
              Either.Left(it)
            }
          }
        )
      }
      type.jvmErasure == Any::class -> AnySchema(description = description)
      else -> define(type)
    }

  /**
   * If a schema for [type] is not yet defined, define it now.
   *
   * @return a [Reference] to the schema for [type].
   */
  private fun Context.define(type: KType): Schema =
    define(type.jvmErasure)

  /**
   * If a schema for [type] is not yet defined, define it now.
   *
   * @return a [Reference] to the schema for [type].
   */
  private fun Context.define(type: KClass<*>, discriminator: Pair<KProperty<String>, String>? = null): Schema =
    if (type.isSingleton) {
      buildSchema(type, discriminator)
    } else {
      val name = checkNotNull(type.simpleName)
      if (!definitions.containsKey(name)) {
        definitions[name] = buildSchema(type, discriminator)
      }
      type.buildRef()
    }

  /**
   * Build a `$ref` URL to the schema for this type.
   */
  private fun KClass<*>.buildRef() =
    Reference("#/${RootSchema::`$defs`.name}/${simpleName}")

  /**
   * Is this something we should represent as an enum?
   */
  private val KType.isEnum: Boolean
    get() = jvmErasure.java.isEnum

  /**
   * Is this something we should represent as a string?
   */
  private val KType.isString: Boolean
    get() = jvmErasure == String::class || jvmErasure in formattedTypes.keys

  /**
   * Is this something we should represent as a boolean?
   */
  private val KType.isBoolean: Boolean
    get() = jvmErasure == Boolean::class

  /**
   * Is this something we should represent as an integer?
   */
  private val KType.isInteger: Boolean
    get() = jvmErasure == Int::class || jvmErasure == Short::class || jvmErasure == Long::class

  /**
   * Is this something we should represent as a number?
   */
  private val KType.isNumber: Boolean
    get() = jvmErasure == Float::class || jvmErasure == Double::class

  /**
   * Is this something we should represent as an array?
   */
  private val KType.isArray: Boolean
    get() = jvmErasure.isSubclassOf(Collection::class) || (javaType as? Class<*>)?.isArray ?: false

  /**
   * Is this something we should represent as a key-value hash?
   */
  private val KType.isMap: Boolean
    get() = jvmErasure.isSubclassOf(Map::class)

  /**
   * Is this an array-like type with unique values?
   */
  private val KType.isUniqueItems: Boolean
    get() = jvmErasure.isSubclassOf(Set::class)

  /**
   * The names of all the values of an enum as they should appear in the schema.
   */
  @Suppress("UNCHECKED_CAST")
  private val KType.enumNames: List<String>
    get() {
      require(isEnum) {
        "enumNames is only valid on enum types"
      }
      return (jvmErasure.java as Class<Enum<*>>).enumConstants.map { it.name }
    }

  /**
   * The element type for a [Collection].
   */
  private val KType.elementType: KType
    get() {
      require(jvmErasure.isSubclassOf(Collection::class) || jvmErasure.java.isArray) {
        "elementType is only valid on Collections"
      }
      return checkNotNull(arguments.first().type) { "unhandled generic type: ${arguments.first()}" }
    }

  /**
   * The value type for a [Collection].
   */
  private val KType.valueType: KType
    get() {
      require(jvmErasure.isSubclassOf(Map::class)) {
        "valueType is only valid on Maps"
      }
      return checkNotNull(arguments[1].type) { "unhandled generic type: ${arguments[1]}" }
    }

  /**
   * The description for this element if it has a [Description] annotation.
   */
  private val KAnnotatedElement.description: String?
    get() = findAnnotation<Description>()?.value

  /**
   * Is this type a singleton object?
   */
  private val KType.isSingleton: Boolean
    get() = jvmErasure.isSingleton

  /**
   * Is this class a singleton object?
   */
  private val KClass<*>.isSingleton: Boolean
    get() = objectInstance != null

  /**
   * Reduces a list of parameters to the names of those that are required.
   */
  private fun Iterable<KParameter>.toRequiredPropertyNames(): SortedSet<String> =
    asSequence()
      .filter { !it.isOptional }
      .filter { !it.hasAnnotation<Optional>() }
      .filter { options.nullableAsOneOf || !it.type.isMarkedNullable }
      .map { checkNotNull(it.name) }
      .toSortedSet(String.CASE_INSENSITIVE_ORDER)

  /**
   * Tries to get the property that is the assignment target of a constructor parameter.
   */
  private fun KClass<*>.backingPropertyFor(param: KParameter): KProperty<*>? =
    memberProperties.find { it.name == param.name } // this is a pretty heinous assumption

  private val formattedTypes = mapOf(
    Duration::class to "duration",
    Instant::class to "date-time",
    ZonedDateTime::class to "date-time",
    OffsetDateTime::class to "date-time",
    LocalDateTime::class to "date-time",
    LocalDate::class to "date",
    LocalTime::class to "time"
  )

  /**
   * The `format` for a string schema, if any.
   */
  private val KType.stringFormat: String?
    get() = formattedTypes[jvmErasure]
}

inline fun <reified TYPE : Any> Generator.generateSchema() = generateSchema(TYPE::class)
