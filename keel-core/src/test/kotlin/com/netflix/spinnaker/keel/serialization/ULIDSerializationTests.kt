package com.netflix.spinnaker.keel.serialization

import com.fasterxml.jackson.databind.node.JsonNodeType.NULL
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import de.huxhorn.sulky.ulid.ULID
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import strikt.jackson.hasNodeType
import strikt.jackson.isTextual
import strikt.jackson.path
import strikt.jackson.textValue

internal object ULIDSerializationTests : JUnit5Minutests {

  val idGenerator = ULID()

  data class Person(
    val id: ULID.Value?,
    val name: String
  )

  data class Fixture(
    val person: Person,
    val deSerializer: ULIDDeserializer = ULIDDeserializer()
  ) {
    val objectMapper = configuredObjectMapper()
  }

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture(Person(id = idGenerator.nextValue(), name = "F Zlem"))
    }

    context("serialization") {
      test("serializes ULID to JSON") {
        val tree = objectMapper
          .valueToTree<ObjectNode>(person)
        expectThat(tree)
          .path("id")
          .isTextual()
          .textValue()
          .isEqualTo(person.id.toString())
      }

      test("serializes null ULID to JSON") {
        val tree = objectMapper
          .valueToTree<ObjectNode>(person.copy(id = null))
        expectThat(tree)
          .path("id")
          .hasNodeType(NULL)
      }
    }

    context("deserialization") {
      test("reads ULID from JSON") {
        val deserialized = objectMapper.readValue<Person>("""
          {
            "id": "${person.id}",
            "name": "${person.name}"
          }
        """)
        expectThat(deserialized)
          .isEqualTo(person)
      }

      test("reads missing ULID as a JSON null") {
        val deserialized = objectMapper.readValue<Person>("""
          {
            "name": "${person.name}"
          }
        """)
        expectThat(deserialized.id).isNull()
      }

      test("reads null ULID as a JSON null") {
        val deserialized = objectMapper.readValue<Person>("""
          {
            "id": null,
            "name": "${person.name}"
          }
        """)
        expectThat(deserialized.id).isNull()
      }
    }
  }
}
