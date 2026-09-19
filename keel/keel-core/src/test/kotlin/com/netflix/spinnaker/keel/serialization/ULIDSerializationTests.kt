package com.netflix.spinnaker.keel.serialization

import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.core.api.randomUID
import de.huxhorn.sulky.ulid.ULID
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import strikt.assertions.isTrue

internal object ULIDSerializationTests : JUnit5Minutests {

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
      Fixture(Person(id = randomUID(), name = "F Zlem"))
    }

    context("serialization") {
      test("serializes ULID to JSON") {
        val tree = objectMapper
          .valueToTree<ObjectNode>(person)
        expectThat(tree.get("id").textValue()).isEqualTo(person.id.toString())
      }

      test("serializes null ULID to JSON") {
        val tree = objectMapper
          .valueToTree<ObjectNode>(person.copy(id = null))
        expectThat(tree.path("id").isMissingNode).isTrue()
      }
    }

    context("deserialization") {
      test("reads ULID from JSON") {
        val deserialized = objectMapper.readValue<Person>(
          """
          {
            "id": "${person.id}",
            "name": "${person.name}"
          }
        """
        )
        expectThat(deserialized)
          .isEqualTo(person)
      }

      test("reads missing ULID as a JSON null") {
        val deserialized = objectMapper.readValue<Person>(
          """
          {
            "name": "${person.name}"
          }
        """
        )
        expectThat(deserialized.id).isNull()
      }

      test("reads null ULID as a JSON null") {
        val deserialized = objectMapper.readValue<Person>(
          """
          {
            "id": null,
            "name": "${person.name}"
          }
        """
        )
        expectThat(deserialized.id).isNull()
      }
    }
  }
}
