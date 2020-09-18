@file:Suppress("JUnit5MalformedNestedClass")

package com.netflix.spinnaker.keel.schema

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.spinnaker.keel.api.schema.Description
import com.netflix.spinnaker.keel.api.schema.Discriminator
import com.netflix.spinnaker.keel.api.schema.Factory
import com.netflix.spinnaker.keel.api.schema.Literal
import com.netflix.spinnaker.keel.api.schema.Optional
import com.netflix.spinnaker.keel.extensions.DefaultExtensionRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.containsExactly
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.containsKey
import strikt.assertions.containsKeys
import strikt.assertions.doesNotContain
import strikt.assertions.filterIsInstance
import strikt.assertions.first
import strikt.assertions.get
import strikt.assertions.hasEntry
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import strikt.assertions.isTrue
import strikt.assertions.one
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.starProjectedType

internal class GeneratorTests {

  abstract class GeneratorTestBase(
    options: Generator.Options = Generator.Options(),
    schemaCustomizers: Collection<SchemaCustomizer> = emptyList()
  ) {
    protected val extensionRegistry = DefaultExtensionRegistry(emptyList())
    private val generator = Generator(extensionRegistry, options, schemaCustomizers)

    inline fun <reified T : Any> generateSchema() =
      generator
        .generateSchema<T>()
        .also {
          jacksonObjectMapper()
            .setSerializationInclusion(NON_NULL)
            .enable(INDENT_OUTPUT)
            .writeValueAsString(it)
            .also(::println)
        }
  }

  @Nested
  @DisplayName("a simple data class")
  class SimpleDataClass : GeneratorTestBase() {
    data class Foo(val string: String)

    val schema by lazy { generateSchema<Foo>() }

    @Test
    fun `documents all properties`() {
      expectThat(schema)
        .get { properties }
        .containsKey(Foo::string.name)
        .get(Foo::string.name)
        .isA<StringSchema>()
    }
  }

  @Nested
  @DisplayName("simple property types")
  class SimplePropertyTypes : GeneratorTestBase() {
    data class Foo(
      val boolean: Boolean,
      val double: Double,
      val integer: Int,
      val string: String
    )

    val schema by lazy { generateSchema<Foo>() }

    @Test
    fun `applies correct property types`() {
      expectThat(schema.properties) {
        get(Foo::boolean.name).isA<BooleanSchema>()
        get(Foo::double.name).isA<NumberSchema>()
        get(Foo::integer.name).isA<IntegerSchema>()
        get(Foo::string.name).isA<StringSchema>()
      }
    }
  }

  @Nested
  @DisplayName("enum properties")
  class EnumProperties : GeneratorTestBase() {
    data class Foo(
      val size: Size
    )

    enum class Size {
      TALL, GRANDE, VENTI
    }

    @Nested
    @DisplayName("with lower-case-enums set to true")
    class LowerCaseEnumsOn : GeneratorTestBase(Generator.Options(lowerCaseEnums = true)) {
      val schema by lazy { generateSchema<Foo>() }

      @Test
      fun `schema is an enum with all enum names lower-cased`() {
        expectThat(schema.properties)
          .get(Foo::size.name)
          .isA<EnumSchema>()
          .get { enum }
          .containsExactly(Size.values().map { it.name.toLowerCase() })
      }
    }

    @Nested
    @DisplayName("with lower-case-enums set to false")
    class LowerCaseEnumsFalse : GeneratorTestBase(Generator.Options(lowerCaseEnums = false)) {
      val schema by lazy { generateSchema<Foo>() }

      @Test
      fun `schema is an enum with all enum names`() {
        expectThat(schema.properties)
          .get(Foo::size.name)
          .isA<EnumSchema>()
          .get { enum }
          .containsExactly(Size.values().map { it.name })
      }
    }
  }

  @Nested
  @DisplayName("properties with default values")
  class OptionalProperties : GeneratorTestBase() {
    data class Foo(
      val optionalString: String = "default value",
      val requiredString: String
    )

    val schema by lazy { generateSchema<Foo>() }

    @Test
    fun `properties with defaults are optional`() {
      expectThat(schema.required)
        .doesNotContain(Foo::optionalString.name)
    }

    @Test
    fun `properties without defaults are required`() {
      expectThat(schema.required)
        .contains(Foo::requiredString.name)
    }
  }

  @Nested
  @DisplayName("nullable properties")
  class NullableProperties : GeneratorTestBase() {
    data class Foo(
      val nullableAny: Any?,
      val nullableBoolean: Boolean?,
      val nullableDouble: Double?,
      val nullableInteger: Int?,
      val nullableObject: Bar?,
      val nullableString: String?
    )

    data class Bar(
      val string: String
    )

    @Nested
    @DisplayName("with nulls-as-one-of option set")
    class NullablesAsOneOfOn : GeneratorTestBase(Generator.Options(nullableAsOneOf = true)) {

      val schema by lazy { generateSchema<Foo>() }

      @Test
      fun `nullable properties are one of null or the usual type`() {
        expectThat(schema.properties) {
          get(Foo::nullableAny.name).isOneOfNullOr<AnySchema>()
          get(Foo::nullableBoolean.name).isOneOfNullOr<BooleanSchema>()
          get(Foo::nullableDouble.name).isOneOfNullOr<NumberSchema>()
          get(Foo::nullableInteger.name).isOneOfNullOr<IntegerSchema>()
          get(Foo::nullableObject.name).isOneOfNullOr<Reference>()
          get(Foo::nullableString.name).isOneOfNullOr<StringSchema>()
        }
      }

      @Test
      fun `nullable properties are required`() {
        expectThat(schema.required)
          .containsExactlyInAnyOrder(
            Foo::nullableAny.name,
            Foo::nullableBoolean.name,
            Foo::nullableDouble.name,
            Foo::nullableInteger.name,
            Foo::nullableObject.name,
            Foo::nullableString.name
          )
      }
    }

    @Nested
    @DisplayName("with nulls-as-one-of option unset")
    class NullablesAsOneOfOff : GeneratorTestBase() {

      val schema by lazy { generateSchema<Foo>() }

      @Test
      fun `nullable properties are not one-of elements`() {
        expectThat(schema.properties) {
          get(Foo::nullableAny.name).isA<AnySchema>()
          get(Foo::nullableBoolean.name).isA<BooleanSchema>()
          get(Foo::nullableDouble.name).isA<NumberSchema>()
          get(Foo::nullableInteger.name).isA<IntegerSchema>()
          get(Foo::nullableObject.name).isA<Reference>()
          get(Foo::nullableString.name).isA<StringSchema>()
        }
      }

      @Test
      fun `nullable properties are optional`() {
        expectThat(schema.required)
          .doesNotContain(
            Foo::nullableAny.name,
            Foo::nullableBoolean.name,
            Foo::nullableDouble.name,
            Foo::nullableInteger.name,
            Foo::nullableObject.name,
            Foo::nullableString.name
          )
      }
    }
  }

  @Nested
  @DisplayName("properties that are type Any")
  class AnyProperties : GeneratorTestBase() {
    data class Foo(
      val any: Any
    )

    val schema by lazy { generateSchema<Foo>() }

    @Test
    fun `any properties are just plain extensible objects`() {
      expectThat(schema.properties[Foo::any.name])
        .isA<AnySchema>()
    }
  }

  @Nested
  @DisplayName("complex properties")
  class ComplexProperties : GeneratorTestBase() {
    data class Foo(
      val bar: Bar
    )

    data class Bar(
      val string: String
    )

    val schema by lazy { generateSchema<Foo>() }

    @Test
    fun `complex property is defined as a $ref`() {
      expectThat(schema.properties[Foo::bar.name])
        .isA<Reference>()
        .get { `$ref` }
        .isEqualTo("#/\$defs/${Bar::class.java.simpleName}")
    }

    @Test
    fun `referenced schema is included in $defs`() {
      expectThat(schema.`$defs`)
        .hasSize(1)
        .get(Bar::class.java.simpleName)
        .isA<ObjectSchema>()
        .get { properties }
        .get(Bar::string.name)
        .isA<StringSchema>()
    }
  }

  @Nested
  @DisplayName("nested complex properties")
  class NestedComplexProperties : GeneratorTestBase() {
    data class Foo(
      val bar: Bar
    )

    data class Bar(
      val baz: Baz
    )

    data class Baz(
      val string: String
    )

    val schema by lazy { generateSchema<Foo>() }

    @Test
    fun `nested complex property is defined as a $ref`() {
      expectThat(schema.`$defs`)
        .get(Bar::class.java.simpleName)
        .isA<ObjectSchema>()
        .get { properties }
        .get(Bar::baz.name)
        .isA<Reference>()
        .get { `$ref` }
        .isEqualTo("#/\$defs/${Baz::class.java.simpleName}")
    }

    @Test
    fun `deep referenced schema is included in $defs`() {
      expectThat(schema.`$defs`)
        .hasSize(2)
        .containsKey(Bar::class.java.simpleName)
        .containsKey(Baz::class.java.simpleName)
        .get(Baz::class.java.simpleName)
        .isA<ObjectSchema>()
        .get { properties }
        .get(Baz::string.name)
        .isA<StringSchema>()
    }
  }

  @Nested
  @DisplayName("sealed classes")
  class SealedClasses : GeneratorTestBase() {
    data class Foo(
      val bar: Bar
    )

    sealed class Bar {
      abstract val string: String

      data class Bar1(override val string: String) : Bar()
      data class Bar2(override val string: String) : Bar()
    }

    val schema by lazy { generateSchema<Foo>() }

    @Test
    fun `sealed class property is a reference to the base type`() {
      expectThat(schema.properties[Foo::bar.name])
        .isA<Reference>()
        .get { `$ref` }
        .isEqualTo("#/\$defs/Bar")
    }

    @Test
    fun `sealed class definition is one of the sub-types`() {
      expectThat(schema.`$defs`[Bar::class.java.simpleName])
        .isA<OneOf>()
        .get { oneOf }
        .one { isA<Reference>().get { `$ref` } isEqualTo "#/\$defs/${Bar.Bar1::class.java.simpleName}" }
        .one { isA<Reference>().get { `$ref` } isEqualTo "#/\$defs/${Bar.Bar2::class.java.simpleName}" }
    }
  }

  @Nested
  @DisplayName("array like properties")
  class ArrayLikeProperties : GeneratorTestBase() {
    data class Foo(
      val listOfStrings: List<String>,
      val setOfStrings: Set<String>,
      val listOfObjects: List<Baz>,
      @Suppress("ArrayInDataClass") val arrayOfStrings: Array<String>
    )

    data class Baz(
      val string: String
    )

    val schema by lazy { generateSchema<Foo>() }

    @TestFactory
    fun arrayAndListProperties() =
      mapOf("list" to Foo::listOfStrings, "array" to Foo::arrayOfStrings)
        .map { (type, property) ->
          dynamicTest("$type property is an array of strings") {
            expectThat(schema.properties[property.name])
              .isA<ArraySchema>()
              .and {
                get { items }.isA<StringSchema>()
                get { uniqueItems }.isNull()
              }
          }
        }

    @Test
    fun `set property is an array of strings with unique items`() {
      expectThat(schema.properties[Foo::setOfStrings.name])
        .isA<ArraySchema>()
        .and {
          get { items }.isA<StringSchema>()
          get { uniqueItems }.isTrue()
        }
    }

    @Test
    fun `list of objects property is an array of refs`() {
      expectThat(schema.properties[Foo::listOfObjects.name])
        .isA<ArraySchema>()
        .and {
          get { items }.isA<Reference>()
        }
    }

    @Test
    fun `referenced schemas are defined`() {
      expectThat(schema.`$defs`[Baz::class.java.simpleName])
        .isA<ObjectSchema>()
    }
  }

  @Nested
  @DisplayName("map properties")
  class MapProperties : GeneratorTestBase() {
    data class Foo(
      val mapOfStrings: Map<String, String>,
      val mapOfObjects: Map<String, Bar>,
      val mapOfAny: Map<String, Any>
    )

    data class Bar(
      val string: String
    )

    val schema by lazy { generateSchema<Foo>() }

    @Test
    fun `map property is represented as an object with additional properties according to its value type`() {
      expectThat(schema.properties[Foo::mapOfStrings.name])
        .isA<MapSchema>()
        .get { additionalProperties }
        .isA<Either.Left<StringSchema, *>>()
    }

    @Test
    fun `map property with object values uses refs`() {
      expectThat(schema.properties[Foo::mapOfObjects.name])
        .isA<MapSchema>()
        .get { additionalProperties }
        .isA<Either.Left<Reference, *>>()
        .get { value.`$ref` }
        .isEqualTo("#/\$defs/${Bar::class.java.simpleName}")
    }

    @Test
    fun `map property with any values uses additionalProperties`() {
      expectThat(schema.properties[Foo::mapOfAny.name])
        .isA<MapSchema>()
        .get { additionalProperties }
        .isA<Either.Right<*, Boolean>>()
        .get { value }
        .isTrue()
    }
  }

  @Nested
  @DisplayName("non-data classes")
  class NonDataClasses : GeneratorTestBase() {
    class Foo(
      val constructorProperty: String,
      constructorParameter: String
    ) {
      val nonConstructorProperty: String
        get() = javaClass.canonicalName
    }

    val schema by lazy { generateSchema<Foo>() }

    @Test
    fun `properties that are part of the constructor are documented`() {
      expectThat(schema.properties[Foo::constructorProperty.name])
        .isA<StringSchema>()
    }

    @Test
    fun `constructor parameters not backed by properties are documented`() {
      expectThat(schema.properties["constructorParameter"])
        .isA<StringSchema>()
    }

    @Test
    fun `properties that are not part of the constructor are not documented`() {
      expectThat(schema.properties[Foo::nonConstructorProperty.name])
        .isNull()
    }
  }

  @Nested
  @DisplayName("Java POJOs")
  class JavaPojos : GeneratorTestBase() {
    val schema by lazy { generateSchema<JavaPojo>() }

    @Test
    fun `constructor parameters are documented`() {
      expectThat(schema.properties)
        .containsKey("arg0")
        .get("arg0")
        .isA<StringSchema>()
    }
  }

  @Nested
  @DisplayName("date and time types")
  class DateAndTimeTypes : GeneratorTestBase() {
    data class Foo(
      val instant: Instant,
      val localDate: LocalDate,
      val localTime: LocalTime,
      val duration: Duration
    )

    val schema by lazy { generateSchema<Foo>() }

    @TestFactory
    fun dateAndTimeFormattedStringProperties() =
      mapOf(
        Foo::instant.name to "datetime",
        Foo::localDate.name to "date",
        Foo::localTime.name to "time",
        Foo::duration.name to "duration"
      ).map { (property, format) ->
        dynamicTest("$property property is a string with '$format' format") {
          expectThat(schema.properties[property])
            .isA<StringSchema>()
            .get { format }
            .isEqualTo(format)
        }
      }
  }

  @Nested
  @DisplayName("non-generic polymorphic types")
  class NonGenericPolymorphicTypes : GeneratorTestBase() {
    // yes Wrapper is generic but Foo isn't
    data class Foo(
      val wrapper: Wrapper<*>
    )

    interface Wrapper<T> {
      @Discriminator
      val type: String
      val value: T
    }

    data class StringWrapper(override val value: String) : Wrapper<String> {
      override val type = "string"
    }

    data class IntegerWrapper(override val value: Int) : Wrapper<Int> {
      override val type = "integer"
    }

    val schema by lazy { generateSchema<Foo>() }

    @BeforeEach
    fun registerSubTypes() {
      with(extensionRegistry) {
        register(Wrapper::class.java, StringWrapper::class.java, "string")
        register(Wrapper::class.java, IntegerWrapper::class.java, "integer")
      }
    }

    @Test
    fun `polymorphic properties are a reference to the base type`() {
      expectThat(schema.properties[Foo::wrapper.name])
        .isA<Reference>()
        .get { `$ref` }
        .isEqualTo("#/\$defs/${Wrapper::class.java.simpleName}")
    }

    @Test
    fun `base type are one of the registered sub-types`() {
      expectThat(schema.`$defs`[Wrapper::class.java.simpleName])
        .isA<OneOf>()
        .get { oneOf }
        .one { isA<Reference>().get { `$ref` } isEqualTo "#/\$defs/${StringWrapper::class.java.simpleName}" }
        .one { isA<Reference>().get { `$ref` } isEqualTo "#/\$defs/${IntegerWrapper::class.java.simpleName}" }
    }

    @Test
    fun `the discriminator property is based on the presence of the @Discriminator annotation`() {
      expectThat(schema.`$defs`[Wrapper::class.java.simpleName])
        .isA<OneOf>()
        .get { discriminator?.propertyName }
        .isEqualTo(Wrapper<*>::type.name)
    }

    @Test
    fun `discriminator mappings tie values to references`() {
      expectThat(schema.`$defs`[Wrapper::class.java.simpleName])
        .isA<OneOf>()
        .get { discriminator?.mapping }
        .isNotNull()
        .hasEntry("string", "#/\$defs/${StringWrapper::class.java.simpleName}")
        .hasEntry("integer", "#/\$defs/${IntegerWrapper::class.java.simpleName}")
    }

    @TestFactory
    fun `the discriminator property in the sub-type is an enum with a single value`() =
      mapOf(
        "string" to StringWrapper::class,
        "integer" to IntegerWrapper::class
      )
        .map { (discriminatorValue, type) ->
          dynamicTest("the discriminator property of ${type.simpleName} is an enum with the single value \"$discriminatorValue\"") {
            expectThat(schema.`$defs`[type.simpleName])
              .isA<ObjectSchema>()
              .get { properties[Wrapper<*>::type.name] }
              .isA<EnumSchema>()
              .get(EnumSchema::enum)
              .containsExactly(discriminatorValue)
          }

          dynamicTest("the discriminator property of ${type.simpleName} is required") {
            expectThat(schema.`$defs`[type.simpleName])
              .isA<ObjectSchema>()
              .get(ObjectSchema::required)
              .contains(Wrapper<*>::type.name)
          }
        }
  }

  @Nested
  @DisplayName("generic polymorphic types")
  class GenericPolymorphicTypes : GeneratorTestBase() {
    data class Foo<T : Thing>(
      val normalProperty: String,
      @Discriminator val type: String,
      val genericProperty: T
    )

    interface Thing

    data class Bar(val bar: String) : Thing
    data class Baz(val baz: String) : Thing

    val schema by lazy { generateSchema<Foo<*>>() }

    @BeforeEach
    fun registerSubTypes() {
      with(extensionRegistry) {
        register(Thing::class.java, Bar::class.java, "bar")
        register(Thing::class.java, Baz::class.java, "baz")
      }
    }

    @Test
    fun `base definition contains only common properties`() {
      expectThat(schema.properties.keys)
        .containsExactlyInAnyOrder(
          Foo<*>::normalProperty.name,
          Foo<*>::type.name
        )
    }

    @Test
    fun `$defs exist for types with each generic sub-type`() {
      expectThat(schema.`$defs`)
        .containsKeys(
          "${Bar::class.java.simpleName}${Foo::class.java.simpleName}",
          "${Baz::class.java.simpleName}${Foo::class.java.simpleName}"
        )
    }

    @Test
    fun `generic sub-types contain all of the base type properties and their own`() {
      expectThat(schema.`$defs`["${Bar::class.java.simpleName}${Foo::class.java.simpleName}"])
        .isA<AllOf>()
        .get { allOf }
        .one { isA<Reference>().get { `$ref` } isEqualTo "#/\$defs/${Foo::class.java.simpleName}" }
        .one { isA<ObjectSchema>().get { properties.keys }.containsExactly(Foo<*>::genericProperty.name) }
    }

    @Test
    fun `title is omitted on allOf elements`() {
      expectThat(schema.`$defs`["${Bar::class.java.simpleName}${Foo::class.java.simpleName}"])
        .isA<AllOf>()
        .get { allOf }
        .filterIsInstance<ObjectSchema>()
        .first()
        .get { title }
        .isNull()
    }

    @Test
    fun `the discriminator is based on the annotated property in the base class`() {
      expectThat(schema.discriminator)
        .isNotNull()
        .and {
          get { propertyName } isEqualTo Foo<*>::type.name
          get { mapping }.containsKeys("bar", "baz")
        }
    }
  }

  @Nested
  @DisplayName("types with @JsonCreator annotated factories")
  class JsonCreatorFactories : GeneratorTestBase() {
    data class Foo(
      val bar: Bar
    ) {
      @Suppress("unused")
      @JsonCreator
      constructor(string: String) : this(Bar(string))
    }

    data class Bar(
      val string: String
    )

    val schema by lazy { generateSchema<Foo>() }

    @Test
    fun `schema is derived from the annotated constructor rather than the default one`() {
      expectThat(schema.properties)
        .containsKey(Bar::string.name)
        .not()
        .containsKey(Foo::bar.name)
    }
  }

  @Nested
  @DisplayName("types with @Factory annotated factories")
  class FactoryFactories : GeneratorTestBase() {
    data class Foo(
      val bar: Bar
    ) {
      @Suppress("unused")
      @Factory
      constructor(string: String) : this(Bar(string))
    }

    data class Bar(
      val string: String
    )

    val schema by lazy { generateSchema<Foo>() }

    @Test
    fun `schema is derived from the annotated constructor rather than the default one`() {
      expectThat(schema.properties)
        .containsKey(Bar::string.name)
        .not()
        .containsKey(Foo::bar.name)
    }
  }

  @Nested
  @DisplayName("@Description annotated elements")
  class DescriptionAnnotations : GeneratorTestBase() {
    @Description("type description")
    data class Foo(
      @Description("property description")
      val string: String,
      @Description("nullable property description")
      val nullableString: String?,
      val bar: Bar,
      val baz: Baz,
      val fnord: Fnord
    )

    @Description("linked type description")
    data class Bar(
      val string: String
    )

    @Description("sealed type description")
    sealed class Baz {
      object Baz1 : Baz()
      object Baz2 : Baz()
    }

    @Description("base type description")
    interface Fnord {
      @Discriminator
      val type: String
    }

    class FnordImpl : Fnord {
      override val type = "fnord"
    }

    val schema by lazy { generateSchema<Foo>() }

    @BeforeEach
    fun registerSubTypes() {
      with(extensionRegistry) {
        register(Fnord::class.java, FnordImpl::class.java, "fnord")
      }
    }

    @Test
    fun `type description is derived from the annotation`() {
      expectThat(schema.description)
        .isNotNull()
        .isEqualTo("type description")
    }

    @Test
    fun `linked type description is derived from the annotation`() {
      expectThat(schema.`$defs`[Bar::class.simpleName]?.description)
        .isNotNull()
        .isEqualTo("linked type description")
    }

    @Test
    fun `property description is derived from the annotation`() {
      expectThat(schema.properties[Foo::string.name]?.description)
        .isNotNull()
        .isEqualTo("property description")
    }

    @Test
    fun `nullable properties can have a description`() {
      expectThat(schema.properties[Foo::nullableString.name])
        .isA<StringSchema>()
        .get { description }
        .isEqualTo("nullable property description")
    }

    @Test
    fun `sealed types can have a description`() {
      expectThat(schema.`$defs`[Baz::class.simpleName]?.description)
        .isEqualTo("sealed type description")
    }

    @Test
    fun `extension base types can have a description`() {
      expectThat(schema.`$defs`[Fnord::class.simpleName]?.description)
        .isEqualTo("base type description")
    }
  }

  @Nested
  @DisplayName("properties with @Literal annotation")
  class LiteralAnnotationOnProperty : GeneratorTestBase() {
    data class Foo(
      val bar: Bar
    )

    @Literal(value = "BAR")
    object Bar

    val schema by lazy { generateSchema<Foo>() }

    @Test
    fun `literals are represented as enum with a single value`() {
      expectThat(schema.properties[Foo::bar.name])
        .isA<EnumSchema>()
        .get { enum }
        .containsExactly("BAR")
    }
  }

  @Nested
  @DisplayName("types with @Literal annotation")
  class LiteralAnnotationOnSubtype : GeneratorTestBase() {
    data class Foo(
      val bar: Bar
    )

    sealed class Bar {
      @Literal(value = "BAR")
      object Bar1 : Bar()

      data class Bar2(val string: String) : Bar()
    }


    val schema by lazy { generateSchema<Foo>() }

    @Test
    fun `literals are represented as enum with a single value`() {
      expectThat(schema.`$defs`[Bar::class.simpleName])
        .isA<OneOf>()
        .get { oneOf }
        .one {
          isA<EnumSchema>()
            .get { enum }
            .containsExactly("BAR")
        }
        .one {
          isA<Reference>()
            .get { `$ref` }
            .isEqualTo("#/\$defs/${Bar.Bar2::class.simpleName}")
        }
    }
  }

  @Nested
  @DisplayName("types with custom schemas")
  class CustomSchemaTypes : GeneratorTestBase(
    schemaCustomizers = listOf(
      object : SchemaCustomizer {
        override fun supports(type: KClass<*>): Boolean = type == Bar::class

        override fun buildSchema(): Schema = StringSchema(description = "custom schema")
      }
    )
  ) {
    data class Foo(
      val bar: Bar
    )

    data class Bar(
      val string: String
    )

    val schema by lazy { generateSchema<Foo>() }

    @Test
    fun `custom schema overrides default`() {
      expectThat(schema.`$defs`[Bar::class.simpleName])
        .isA<StringSchema>()
        .get { description }
        .isEqualTo("custom schema")
    }
  }

  @Nested
  @DisplayName("properties with custom schemas")
  class CustomSchemaProperties : GeneratorTestBase(
    schemaCustomizers = listOf(
      object : SchemaCustomizer {
        override fun supports(type: KClass<*>): Boolean = type == Size::class

        override fun buildSchema(): Schema = EnumSchema(
          enum = Size.values().map { it.starbucks },
          description = null
        )
      }
    )
  ) {
    data class Foo(
      val size: Size
    )

    enum class Size(val starbucks: String) {
      SMALL("tall"), MEDIUM("grande"), LARGE("venti")
    }

    val schema by lazy { generateSchema<Foo>() }

    @Test
    fun `custom schema overrides default`() {
      expectThat(schema.properties[Foo::size.name])
        .isA<EnumSchema>()
        .get { enum }
        .containsExactlyInAnyOrder(Size.values().map { it.starbucks })
    }
  }

  @Nested
  @DisplayName("forced optional properties")
  class OptionalAnnotations : GeneratorTestBase() {
    data class Foo(
      @param:Optional val string: String
    )

    val schema by lazy { generateSchema<Foo>() }

    @Test
    fun `properties annotated with @Optional are not required even if they normally would be`() {
      expectThat(schema.required)
        .doesNotContain(Foo::string.name)
    }
  }
}

inline fun <reified T> Assertion.Builder<Schema?>.isOneOfNullOr() {
  isA<OneOf>()
    .get { oneOf }
    .hasSize(2)
    .one { isA<NullSchema>() }
    .one { isA<T>() }
}
