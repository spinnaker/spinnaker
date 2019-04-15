package com.netflix.spinnaker.keel.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import de.danielbechler.diff.ObjectDifferBuilder
import de.danielbechler.diff.node.DiffNode
import de.danielbechler.diff.node.Visit
import de.danielbechler.util.Strings
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo


internal class DiffNodeSerializationTests : JUnit5Minutests {

  internal data class Fixture(
    val current: DummyValue?,
    val previous: DummyValue?,
    val mapper: ObjectMapper = configuredObjectMapper()
  ) {
    private val differ = ObjectDifferBuilder.buildDefault()

    val diff: DiffNode
      get() = differ.compare(current, previous)

    fun DiffNode.toJson(): Map<String, Any?> =
      JsonVisitor(mapper, current, previous)
        .also { visit(it) }
        .messages
  }

  fun tests() = rootContext<Fixture> {
    context("no delta") {
      fixture {
        Fixture(
          current = DummyValue("fnord"),
          previous = DummyValue("fnord")
        )
      }

      test("serialized output is empty") {
        val json = diff.toJson()

        expectThat(json).isEqualTo(emptyMap())
      }
    }

    context("delta in simple field") {
      fixture {
        Fixture(
          current = DummyValue("FNORD"),
          previous = DummyValue()
        )
      }

      test("serialized output contains simple property details") {
        val json = diff.toJson()
        val expected = mapper.readValue<Map<String, Any?>>("""
          |{
          |  "/AString": {
          |    "state": "CHANGED",
          |    "working": "FNORD",
          |    "base": "fnord"
          |  }
          |}""".trimMargin())

        expectThat(json).isEqualTo(expected)
      }
    }

    context("delta in nullable field") {
      fixture {
        Fixture(
          current = DummyValue(aNullableString = "fnord"),
          previous = DummyValue()
        )
      }

      test("serialized output contains property details") {
        val json = diff.toJson()
        val expected = mapper.readValue<Map<String, Any?>>("""
          |{
          |  "/ANullableString": {
          |    "state": "ADDED",
          |    "working": "fnord",
          |    "base": null
          |  }
          |}""".trimMargin())

        expectThat(json).isEqualTo(expected)
      }
    }

    context("delta in nested field") {
      fixture {
        Fixture(
          current = DummyValue(aNestedValue = DummyValue("FNORD", aNullableString = "fnord")),
          previous = DummyValue(aNestedValue = DummyValue())
        )
      }

      test("serialized output contains nested property details") {
        val json = diff.toJson()
        val expected = mapper.readValue<Map<String, Any?>>("""
          |{
          |  "/ANestedValue": {
          |    "state": "CHANGED"
          |  },
          |  "/ANestedValue/ANullableString": {
          |    "state": "ADDED",
          |    "working": "fnord",
          |    "base": null
          |  },
          |  "/ANestedValue/AString": {
          |    "state": "CHANGED",
          |    "working": "FNORD",
          |    "base": "fnord"
          |  }
          |}""".trimMargin())

        expectThat(json).isEqualTo(expected)
      }
    }

    context("delta in a list") {
      context("elements added") {
        fixture {
          Fixture(
            current = DummyValue(aList = listOf("foo", "bar")),
            previous = DummyValue()
          )
        }

        test("serialized output contains nested property details") {
          val json = diff.toJson()
          val expected = mapper.readValue<Map<String, Any?>>("""
            |{
            |  "/AList": {
            |    "state": "CHANGED"
            |  },
            |  "/AList[foo]": {
            |    "state": "ADDED",
            |    "working": "foo",
            |    "base": null
            |  },
            |  "/AList[bar]": {
            |    "state": "ADDED",
            |    "working": "bar",
            |    "base": null
            |  }
            |}""".trimMargin())

          expectThat(json).isEqualTo(expected)
        }
      }

      context("elements changed") {
        fixture {
          Fixture(
            current = DummyValue(aList = listOf("bar", "baz")),
            previous = DummyValue(aList = listOf("foo", "bar"))
          )
        }

        test("serialized output contains nested property details") {
          val json = diff.toJson()
          val expected = mapper.readValue<Map<String, Any?>>("""
            |{
            |  "/AList": {
            |    "state": "CHANGED"
            |  },
            |  "/AList[baz]": {
            |    "state": "ADDED",
            |    "working": "baz",
            |    "base": null
            |  },
            |  "/AList[foo]": {
            |    "state": "REMOVED",
            |    "working": null,
            |    "base": "foo"
            |  }
            |}""".trimMargin())

          expectThat(json).isEqualTo(expected)
        }
      }
    }
  }
}

internal data class DummyValue(
  val aString: String = "fnord",
  val aNullableString: String? = null,
  val aNestedValue: DummyValue? = null,
  val aList: List<String> = emptyList(),
  val aMap: Map<String, Any?> = emptyMap()
)

class JsonVisitor(
  private val objectMapper: ObjectMapper,
  private val working: Any?,
  private val base: Any?
) : DiffNode.Visitor {
  val messages: Map<String, Any?>
    get() = _messages

  private val _messages = mutableMapOf<String, Map<String, Any?>>()

  override fun node(node: DiffNode, visit: Visit) {
    if (!node.isRootNode) {
      val message = mutableMapOf<String, Any?>("state" to node.state.name)
      if (!node.hasChildren()) {
        message["working"] = node.canonicalGet(working).let(Strings::toSingleLineString)
        message["base"] = node.canonicalGet(base).let(Strings::toSingleLineString)
      }
      _messages[node.path.toString()] = message
    }
  }

  fun toJsonString(): String {
    println(_messages)
    return objectMapper.writeValueAsString(_messages)
  }
}
