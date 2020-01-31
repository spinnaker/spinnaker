package com.netflix.spinnaker.keel.diff

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal class ResourceDiffSerializationTests : JUnit5Minutests {

  internal data class Fixture(
    val working: DummyValue,
    val base: DummyValue?,
    val mapper: ObjectMapper = configuredObjectMapper()
  ) {
    val diff: ResourceDiff<DummyValue>
      get() = DefaultResourceDiff(working, base)
  }

  fun tests() = rootContext<Fixture> {
    context("no delta") {
      fixture {
        Fixture(
          working = DummyValue("fnord"),
          base = DummyValue("fnord")
        )
      }

      test("serialized output is empty") {
        val json = diff.toDeltaJson()

        expectThat(json).isEqualTo(emptyMap())
      }
    }

    context("delta in simple field") {
      fixture {
        Fixture(
          working = DummyValue("FNORD"),
          base = DummyValue()
        )
      }

      test("serialized output contains simple property details") {
        val json = diff.toDeltaJson()
        val expected = mapper.readValue<Map<String, Any?>>("""
          |{
          |  "/AString": {
          |    "state": "CHANGED",
          |    "desired": "FNORD",
          |    "current": "fnord"
          |  }
          |}""".trimMargin())

        expectThat(json).isEqualTo(expected)
      }
    }

    context("delta in nullable field") {
      fixture {
        Fixture(
          working = DummyValue(aNullableString = "fnord"),
          base = DummyValue()
        )
      }

      test("serialized output contains property details") {
        val json = diff.toDeltaJson()
        val expected = mapper.readValue<Map<String, Any?>>("""
          |{
          |  "/ANullableString": {
          |    "state": "ADDED",
          |    "desired": "fnord",
          |    "current": null
          |  }
          |}""".trimMargin())

        expectThat(json).isEqualTo(expected)
      }
    }

    context("delta in nested field") {
      fixture {
        Fixture(
          working = DummyValue(aNestedValue = DummyValue("FNORD", aNullableString = "fnord")),
          base = DummyValue(aNestedValue = DummyValue())
        )
      }

      test("serialized output contains nested property details") {
        val json = diff.toDeltaJson()
        val expected = mapper.readValue<Map<String, Any?>>("""
          |{
          |  "/ANestedValue": {
          |    "state": "CHANGED"
          |  },
          |  "/ANestedValue/ANullableString": {
          |    "state": "ADDED",
          |    "desired": "fnord",
          |    "current": null
          |  },
          |  "/ANestedValue/AString": {
          |    "state": "CHANGED",
          |    "desired": "FNORD",
          |    "current": "fnord"
          |  }
          |}""".trimMargin())

        expectThat(json).isEqualTo(expected)
      }
    }

    context("delta in a list") {
      context("elements added") {
        fixture {
          Fixture(
            working = DummyValue(aList = listOf("foo", "bar")),
            base = DummyValue()
          )
        }

        test("serialized output contains nested property details") {
          val json = diff.toDeltaJson()
          val expected = mapper.readValue<Map<String, Any?>>("""
            |{
            |  "/AList": {
            |    "state": "CHANGED"
            |  },
            |  "/AList[foo]": {
            |    "state": "ADDED",
            |    "desired": "foo",
            |    "current": null
            |  },
            |  "/AList[bar]": {
            |    "state": "ADDED",
            |    "desired": "bar",
            |    "current": null
            |  }
            |}""".trimMargin())

          expectThat(json).isEqualTo(expected)
        }
      }

      context("elements changed") {
        fixture {
          Fixture(
            working = DummyValue(aList = listOf("bar", "baz")),
            base = DummyValue(aList = listOf("foo", "bar"))
          )
        }

        test("serialized output contains nested property details") {
          val json = diff.toDeltaJson()
          val expected = mapper.readValue<Map<String, Any?>>("""
            |{
            |  "/AList": {
            |    "state": "CHANGED"
            |  },
            |  "/AList[baz]": {
            |    "state": "ADDED",
            |    "desired": "baz",
            |    "current": null
            |  },
            |  "/AList[foo]": {
            |    "state": "REMOVED",
            |    "desired": null,
            |    "current": "foo"
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
