package com.netflix.spinnaker.keel.jsonschema

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass
import strikt.api.Assertion
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.any
import strikt.assertions.contains
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.failed
import strikt.assertions.first
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue
import strikt.assertions.map
import strikt.assertions.succeeded
import strikt.jackson.at
import strikt.jackson.booleanValue
import strikt.jackson.has
import strikt.jackson.hasSize
import strikt.jackson.isArray
import strikt.jackson.isBoolean
import strikt.jackson.isMissing
import strikt.jackson.isObject
import strikt.jackson.path
import strikt.jackson.textValue

/**
 * To-do:
 *
 * - descriptions
 * - handle @JsonTypeInfo on properties rather than on base type
 * - handle other types than `As.PROPERTY`
 * - handle ToStringSerializer on property
 * - any other serializer / deserializer (error?)
 */
internal class JsonSchemaGeneratorTests : JUnit5Minutests {
  data class Fixture(
    val type: KClass<*>,
    private val config: SchemaGeneratorConfiguration
  ) {
    val mapper = configuredObjectMapper()
    private val generator = JsonSchemaGenerator(mapper, config)

    val schema: JsonNode by lazy {
      generator.generate(type, "http://whatever.io/${type.simpleName}.json.schema")
        .also {
          mapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(it)
            .let(::println)
        }
    }
  }

  @Suppress("TestFunctionName")
  inline fun <reified T> Fixture(
    config: SchemaGeneratorConfiguration = SchemaGeneratorConfiguration()
  ) = Fixture(T::class, config)

  @Suppress("unused")
  fun tests() = rootContext<Fixture> {
    context("simple properties") {
      fixture { Fixture<SimpleProperties>() }

      test("is an object") {
        expectThat(schema)
          .at("/type")
          .textValue()
          .isEqualTo("object")
      }

      test("contains all properties") {
        expectThat(schema)
          .at("/properties")
          .has("aString")
          .has("aNumber")
          .has("aBoolean")
          .has("anEnum")
      }

      context("properties have the correct types") {
        test("string property is type 'string'") {
          expectThat(schema)
            .at("/properties/aString/type")
            .textValue()
            .isEqualTo("string")
        }

        test("number property is type 'number'") {
          expectThat(schema)
            .at("/properties/aNumber/type")
            .textValue()
            .isEqualTo("number")
        }

        test("boolean property is type 'boolean'") {
          expectThat(schema)
            .at("/properties/aBoolean/type")
            .textValue()
            .isEqualTo("boolean")
        }

        test("enum property is an 'enum'") {
          expectThat(schema)
            .at("/properties/anEnum")
            .doesNotHave("type")
            .path("enum")
            .isArray()
            .map { it.textValue() }
            .containsExactlyInAnyOrder(SimpleEnum.values().map { it.name })
        }
      }

      // this just validates my assumption about how Jackson works
      test("cannot parse json with missing non-nullable properties") {
        expectCatching {
          mapper.readValue<SimpleProperties>("{}")
        }
          .failed()
          .isA<JsonMappingException>()
      }

      // this just validates my assumption about how Jackson works
      test("cannot parse json with null for non-nullable properties") {
        expectCatching {
          mapper.readValue<SimpleProperties>("""{"aString":null,"aNumber":1,"aBoolean":true,"anEnum":"catflap"}""")
        }
          .failed()
          .isA<JsonMappingException>()
      }

      test("non-defaulted properties are required") {
        expectThat(schema)
          .at("/required")
          .isArray()
          .map { it.textValue() }
          .containsExactlyInAnyOrder("aString", "aNumber", "aBoolean", "anEnum")
      }
    }

    context("object and array properties") {
      fixture { Fixture<ComplexProperties>() }

      context("properties have the correct types") {
        test("object property is a reference") {
          expectThat(schema)
            .at("/properties/anObject/\$ref")
            .textValue()
            .isEqualTo("#/\$defs/SimpleProperties")
        }

        test("object property is defined externally") {
          expectThat(schema) {
            at("/\$defs/SimpleProperties/type")
              .textValue()
              .isEqualTo("object")
            at("/\$defs/SimpleProperties/properties")
              .has("aString")
              .has("aNumber")
              .has("aBoolean")
              .has("anEnum")
            at("/\$defs/SimpleProperties/required")
              .isArray()
              .map { it.textValue() }
              .containsExactlyInAnyOrder("aString", "aNumber", "aBoolean", "anEnum")
          }
        }

        sequenceOf("string", "number", "boolean").forEach { items ->
          sequenceOf("Array", "List").forEach { container ->
            test("${container.toLowerCase()} property is type 'array'") {
              expectThat(schema)
                .at("/properties/$items$container/type")
                .textValue()
                .isEqualTo("array")
            }

            test("${container.toLowerCase()} of $items has items of type '$items'") {
              expectThat(schema)
                .at("/properties/$items$container/items/type")
                .textValue()
                .isEqualTo(items)
            }
          }
        }

        sequenceOf("Array", "List").forEach { container ->
          test("object property is type 'array'") {
            expectThat(schema)
              .at("/properties/object$container/type")
              .textValue()
              .isEqualTo("array")
          }

          test("${container.toLowerCase()} of object has items that are references") {
            expectThat(schema)
              .at("/properties/object$container/items/\$ref")
              .textValue()
              .isEqualTo("#/\$defs/SimpleProperties")
          }
        }
      }
    }

    context("nullable properties") {
      context("using default configuration") {
        fixture { Fixture<NullableProperties>() }

        // this just validates my assumption about how Jackson works
        test("can parse json with missing nullable properties") {
          expectCatching {
            mapper.readValue<NullableProperties>("""{"aString":null}""")
          }
            .succeeded()
            .isEqualTo(NullableProperties(null, null, null, null, null, null))
        }

        test("nullable properties are not required") {
          expectThat(schema)
            .at("/required")
            .isArray()
            .isEmpty()
        }

        context("nullable properties' types are the same as their non-nullable type") {
          test("string property's type 'string'") {
            expectThat(schema)
              .at("/properties/aString/type")
              .textValue()
              .isEqualTo("string")
          }

          test("number property's type is 'number'") {
            expectThat(schema)
              .at("/properties/aNumber/type")
              .textValue()
              .isEqualTo("number")
          }

          test("boolean property's type is 'boolean'") {
            expectThat(schema)
              .at("/properties/aBoolean/type")
              .textValue()
              .isEqualTo("boolean")
          }

          test("enum property uses 'enum'") {
            expectThat(schema)
              .at("/properties/anEnum/enum")
              .isArray()
              .map { it.textValue() }
              .containsExactlyInAnyOrder(SimpleEnum.values().map { it.name })
          }

          test("object property is a reference") {
            expectThat(schema)
              .at("/properties/anObject/\$ref")
              .textValue()
              .isEqualTo("#/\$defs/SimpleProperties")
          }

          test("array property's type is 'array'") {
            expectThat(schema)
              .at("/properties/stringList/type")
              .textValue()
              .isEqualTo("array")
          }

          test("array property's item type is defined") {
            expectThat(schema)
              .at("/properties/stringList/items/type")
              .textValue()
              .isEqualTo("string")
          }
        }
      }

      context("when configured to represent nullable properties using oneOf") {
        fixture {
          Fixture<NullableProperties>(
            config = SchemaGeneratorConfiguration(
              nullableTypesAsOneOf = true
            )
          )
        }

        test("nullable properties are not required") {
          expectThat(schema)
            .at("/required")
            .isArray()
            .isEmpty()
        }

        context("nullable properties' types are one of 'null' or their default type") {
          test("string property's type is one of 'null' or 'string'") {
            expectThat(schema)
              .at("/properties/aString/oneOf")
              .isArray()
              .map { it.path("type").textValue() }
              .containsExactlyInAnyOrder("null", "string")
          }

          test("number property's type is one of 'null' or 'number'") {
            expectThat(schema)
              .at("/properties/aNumber/oneOf")
              .isArray()
              .map { it.path("type").textValue() }
              .containsExactlyInAnyOrder("null", "number")
          }

          test("boolean property's type is one of 'null' or 'boolean'") {
            expectThat(schema)
              .at("/properties/aBoolean/oneOf")
              .isArray()
              .map { it.path("type").textValue() }
              .containsExactlyInAnyOrder("null", "boolean")
          }

          test("enum property may be one of 'null' or 'enum'") {
            expectThat(schema)
              .at("/properties/anEnum/oneOf")
              .isArray()
              .and {
                first { it.has("type") }
                  .path("type")
                  .textValue()
                  .isEqualTo("null")
                first { it.has("enum") }
                  .path("enum")
                  .isArray()
                  .map { it.textValue() }
                  .containsExactlyInAnyOrder(SimpleEnum.values().map { it.name })
              }
          }

          test("object property is one of 'null' or a reference") {
            expectThat(schema)
              .at("/properties/anObject/oneOf")
              .isArray()
              .hasSize(2)
              .any {
                has("type")
                  .path("type")
                  .textValue()
                  .isEqualTo("null")
              }
              .any {
                has("\$ref")
                  .path("\$ref")
                  .textValue()
                  .isEqualTo("#/\$defs/SimpleProperties")
              }
          }

          test("object is defined") {
            expectThat(schema) {
              at("/\$defs/SimpleProperties/properties")
                .has("aString")
                .has("aNumber")
                .has("aBoolean")
                .has("anEnum")
              at("/\$defs/SimpleProperties/required")
                .isArray()
                .map { it.textValue() }
                .containsExactlyInAnyOrder("aString", "aNumber", "aBoolean", "anEnum")
            }
          }

          test("array property's type is one of 'null' or 'array'") {
            expectThat(schema)
              .at("/properties/stringList/oneOf")
              .isArray()
              .map { it.path("type").textValue() }
              .containsExactlyInAnyOrder("null", "array")
          }

          test("array property's item types are nested under oneOf") {
            expectThat(schema)
              .at("/properties/stringList/oneOf")
              .isArray()
              .first { it.path("type").textValue() == "array" }
              .path("items")
              .path("type")
              .textValue()
              .isEqualTo("string")
          }
        }
      }
    }

    context("defaulted properties") {
      fixture { Fixture<DefaultSimpleProperties>() }

      test("defaulted properties are not required") {
        expectThat(schema)
          .at("/required")
          .isArray()
          .isEmpty()
      }
    }

    context("a class that uses @JsonCreator") {
      fixture { Fixture<UsesJsonCreator>() }

      test("properties are based on the @JsonCreator parameters") {
        expectThat(schema)
          .at("/properties")
          .has("aString")
          .has("aDefaultString")
          .has("aNullableString")
      }
    }

    context("unique item properties") {
      fixture { Fixture<SetProperty>() }

      test("properties that are sets must have unique items") {
        expectThat(schema)
          .at("/properties/aSet/uniqueItems")
          .isBoolean()
          .booleanValue()
          .isTrue()
      }

      test("other collection types do not require unique items") {
        expectThat(schema)
          .at("/properties/aList")
          .doesNotHave("uniqueItems")
      }
    }

    context("properties with sub-types") {
      fixture { Fixture<AbstractProperty>() }

      test("abstract property omits type") {
        expectThat(schema)
          .at("/properties/anInterface/type")
          .isMissing()
      }

      test("property is a reference to the base type") {
        expectThat(schema)
          .at("/properties/anInterface/\$ref")
          .textValue()
          .isEqualTo("#/\$defs/Animal")
      }

      test("base type definition must be one of the implementations") {
        expectThat(schema)
          .at("/\$defs/Animal/oneOf")
          .isArray()
          .map {
            it.path("\$ref").textValue()
          }
          .containsExactlyInAnyOrder(
            "#/\$defs/Cat",
            "#/\$defs/Dog"
          )
      }

      test("implementations are listed in definitions") {
        expectThat(schema) {
          at("/\$defs/Cat/properties")
            .has("name")
            .has("color")
          at("/\$defs/Cat/type")
            .textValue()
            .isEqualTo("object")

          at("/\$defs/Dog/properties")
            .has("name")
            .has("size")
          at("/\$defs/Dog/type")
            .textValue()
            .isEqualTo("object")
        }
      }

      test("implementations have type id property") {
        expectThat(schema) {
          at("/\$defs/Cat/properties/@type/enum")
            .isArray()
            .map { it.textValue() }
            .contains("cat")
          at("/\$defs/Dog/properties/@type/enum")
            .isArray()
            .map { it.textValue() }
            .contains("dog")
        }
      }

      test("typeId property is required") {
        expectThat(schema) {
          at("/\$defs/Cat/required")
            .isArray()
            .map { it.textValue() }
            .contains("@type")
          at("/\$defs/Dog/required")
            .isArray()
            .map { it.textValue() }
            .contains("@type")
        }
      }
    }

    context("properties with sub-types registered at runtime") {
      fixture { Fixture<RuntimeRegisteredSubtypes>() }

      before {
        mapper.registerSubtypes(Bicycle::class.java, Skateboard::class.java)
      }

      test("subtypes are identified") {
        expectThat(schema)
          .at("/\$defs/Vehicle/oneOf")
          .isArray()
          .map {
            it.path("\$ref").textValue()
          }
          .containsExactlyInAnyOrder(
            "#/\$defs/Bicycle",
            "#/\$defs/Skateboard"
          )
      }
    }

    context("array of subtypes") {
      fixture { Fixture<ArrayOfSubtypes>() }

      test("base type is reference in array items") {
        expectThat(schema)
          .at("/properties/pets/items/\$ref")
          .textValue()
          .isEqualTo("#/\$defs/Animal")
      }

      test("subtypes are references in base type definition") {
        expectThat(schema)
          .at("/\$defs/Animal/oneOf")
          .isArray()
          .map {
            it.path("\$ref").textValue()
          }
          .containsExactlyInAnyOrder(
            "#/\$defs/Cat",
            "#/\$defs/Dog"
          )
      }
    }

    context("object properties that are serialized as string") {
      fixture { Fixture<ToStringSerializedProperty>() }

      test("property has type 'string'") {
        expectThat(schema)
          .at("/properties/structuredTypeSerializedAsString/type")
          .textValue()
          .isEqualTo("string")
      }
    }

    context("supported Java type properties") {
      fixture { Fixture<JavaTimeProperties>() }

      test("instant properties are represented as type 'string' with a specified format") {
        expectThat(schema) {
          at("/properties/anInstant/type")
            .textValue()
            .isEqualTo("string")
          at("/properties/anInstant/format")
            .textValue()
            .isEqualTo("date-time")
        }
      }

      test("duration properties are represented as type 'string' with a specified format") {
        expectThat(schema) {
          at("/properties/aDuration/type")
            .textValue()
            .isEqualTo("string")
          at("/properties/aDuration/format")
            .textValue()
            .isEqualTo("duration")
        }
      }
    }

    context("arrays of objects") {
      fixture { Fixture<ArrayOfObjects>() }

      test("object definition is referenced inside items") {
        expectThat(schema)
          .at("/properties/someObjects/items/\$ref")
          .textValue()
          .isEqualTo("#/\$defs/SimpleProperties")
      }

      test("object is defined") {
        expectThat(schema) {
          at("/\$defs/SimpleProperties/properties")
            .has("aString")
            .has("aNumber")
            .has("aBoolean")
            .has("anEnum")
          at("/\$defs/SimpleProperties/required")
            .isArray()
            .map { it.textValue() }
            .containsExactlyInAnyOrder("aString", "aNumber", "aBoolean", "anEnum")
        }
      }
    }

    context("arrays of objects of a sealed class") {
      fixture { Fixture<ArrayOfSealedTypes>() }

      test("items are reference to sealed type") {
        expectThat(schema)
          .at("/properties/sealedTypeList/items/\$ref")
          .textValue()
          .isEqualTo("#/\$defs/Liquor")
      }

      test("sealed sub types are referenced in sealed type definition") {
        expectThat(schema)
          .at("/\$defs/Liquor/oneOf")
          .isArray()
          .map {
            it.path("\$ref").textValue()
          }
          .containsExactlyInAnyOrder(
            "#/\$defs/Whisky",
            "#/\$defs/Gin",
            "#/\$defs/Vodka"
          )
      }

      test("sealed sub types have their own definitions") {
        expectThat(schema) {
          at("/\$defs/Whisky").isObject()
          at("/\$defs/Gin").isObject()
          at("/\$defs/Vodka").isObject()
        }
      }
    }

    context("multi-level object properties") {
      fixture { Fixture<MultiLevelObjectProperties>() }

      test("first level objects are referenced") {
        expectThat(schema)
          .at("/properties/firstLevel/\$ref")
          .textValue()
          .isEqualTo("#/\$defs/NestedObjectProperties")
      }

      test("first level objects are defined") {
        expectThat(schema)
          .at("/\$defs/NestedObjectProperties/properties")
          .has("secondLevel")
      }

      test("second level objects are referenced") {
        expectThat(schema)
          .at("/\$defs/NestedObjectProperties/properties/secondLevel/\$ref")
          .textValue()
          .isEqualTo("#/\$defs/SimpleProperties")
      }

      test("second level objects are defined") {
        expectThat(schema) {
          at("/\$defs")
            .has("SimpleProperties")
          at("/\$defs/SimpleProperties/properties")
            .has("aString")
            .has("aNumber")
            .has("aBoolean")
            .has("anEnum")
        }
      }
    }

    context("map and any properties") {
      fixture { Fixture<MapAndAnyProperties>() }

      mapOf(
        "aMap" to Map::class,
        "anAny" to Any::class
      ).forEach { (fieldName, type) ->
        test("${type.simpleName} properties are plain objects") {
          expectThat(schema) {
            at("/properties/$fieldName/type")
              .textValue()
              .isEqualTo("object")
            at("/properties/$fieldName/properties")
              .isMissing()
            at("/properties/$fieldName/required")
              .isMissing()
          }
        }

        test("${type.simpleName} types don't get a definition") {
          expectThat(schema)
            .at("/\$defs/${type.simpleName}")
            .isMissing()
        }
      }
    }
  }
}

fun <T : JsonNode> Assertion.Builder<T>.doesNotHave(fieldName: String): Assertion.Builder<T> =
  assert("does not have a field named '$fieldName'") { subject ->
    if (subject.has(fieldName)) {
      fail(subject.fields().asSequence().map { it.key }.toList())
    } else {
      pass()
    }
  }

fun Assertion.Builder<ArrayNode>.isEmpty(): Assertion.Builder<ArrayNode> =
  assert("is empty") {
    if (it.size() == 0) pass()
    else fail(it.elements().asSequence().toList())
  }

@Suppress("unused", "EnumEntryName")
private enum class SimpleEnum {
  catflap, rubberplant, marzipan
}

private data class SimpleProperties(
  val aString: String,
  val aNumber: Int,
  val aBoolean: Boolean,
  val anEnum: SimpleEnum
)

@Suppress("ArrayInDataClass")
private data class ComplexProperties(
  val anObject: SimpleProperties,
  val stringArray: Array<String>,
  val numberArray: Array<Number>,
  val booleanArray: Array<Boolean>,
  val objectArray: Array<SimpleProperties>,
  val stringList: List<String>,
  val numberList: List<Number>,
  val booleanList: List<Boolean>,
  val objectList: List<SimpleProperties>
)

private data class NullableProperties(
  val aString: String?,
  val aNumber: Int?,
  val aBoolean: Boolean?,
  val anEnum: SimpleEnum?,
  val anObject: SimpleProperties?,
  val stringList: List<String>?
)

private data class DefaultSimpleProperties(
  val aString: String = "o hai",
  val aNumber: Int = 1337,
  val aBoolean: Boolean = true
)

private data class UsesJsonCreator(
  val allTheProperties: Map<String, Any?>
) {
  @Suppress("unused")
  @JsonCreator
  constructor(
    aString: String,
    aDefaultString: String = "o hai",
    aNullableString: String?
  ) : this(
    mapOf(
      "aString" to aString,
      "aDefaultString" to aDefaultString,
      "aNullableString" to aNullableString
    )
  )
}

private data class SetProperty(
  val aList: List<String>,
  val aSet: Set<String>
)

private data class AbstractProperty(
  val anInterface: Animal
)

@JsonTypeInfo(
  use = Id.NAME,
  include = As.PROPERTY
)
@JsonSubTypes(
  JsonSubTypes.Type(Cat::class, name = "cat"),
  JsonSubTypes.Type(Dog::class, name = "dog")
)
private interface Animal

private data class Cat(
  val name: String,
  val color: String
) : Animal

private data class Dog(
  val name: String,
  val size: Number
) : Animal

private data class RuntimeRegisteredSubtypes(
  @JsonTypeInfo(
    use = Id.MINIMAL_CLASS,
    include = As.EXTERNAL_PROPERTY,
    property = "vehicleKind"
  )
  val anInterfaceWithUnknownSubtypes: Vehicle
)

private interface Vehicle

private data class Bicycle(
  val frame: String
) : Vehicle

private data class Skateboard(
  val material: String
) : Vehicle

private data class ArrayOfSubtypes(
  val pets: List<Animal>
)

private data class ToStringSerializedProperty(
  val structuredTypeSerializedAsString: WebAddress
)

@JsonSerialize(using = ToStringSerializer::class)
private data class WebAddress(
  val scheme: String,
  val host: String,
  val path: String
) {
  override fun toString() = "$scheme://$host/$path"
}

private data class JavaTimeProperties(
  val anInstant: Instant,
  val aDuration: Duration
)

private data class ArrayOfObjects(
  val someObjects: List<SimpleProperties>
)

data class ArrayOfSealedTypes(
  val sealedTypeList: List<Liquor>
)

sealed class Liquor

data class Whisky(
  val type: String
) : Liquor()

data class Gin(
  val floral: Boolean
) : Liquor()

object Vodka : Liquor()

private data class MultiLevelObjectProperties(
  val firstLevel: NestedObjectProperties
)

private data class NestedObjectProperties(
  val secondLevel: SimpleProperties
)

private data class MapAndAnyProperties(
  val aMap: Map<String, Any?>,
  val anAny: Any
)
