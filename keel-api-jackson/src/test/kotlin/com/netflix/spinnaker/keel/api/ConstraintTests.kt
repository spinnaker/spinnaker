package com.netflix.spinnaker.keel.api

import com.fasterxml.jackson.databind.jsontype.NamedType
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isA

internal class ConstraintTests : JUnit5Minutests {
  fun tests() = rootContext<Fixture> {
    context("json representing a sub-type of constraint") {
      fixture {
        Fixture(
          """
          {
            "type": "depends-on",
            "environment": "test"
          }
          """.trimIndent()
        )
      }

      test("parses correct constraint type") {
        expectThat(parse<Constraint>())
          .isA<DependsOnConstraint>()
      }
    }

    context("json representing a delivery config with a constraint") {
      fixture {
        Fixture(
          """
          {
            "name": "a-delivery-config",
            "application": "fnord",
            "serviceAccount": "keel@spinnaker",
            "environments": [
              {
                "name": "test"
              },
              {
                "name": "prod",
                "constraints": [
                  {
                    "type": "depends-on",
                    "environment": "test"
                  }
                ]
              }
            ]
          }
          """.trimIndent()
        )
      }

      test("parses correct constraint type") {
        expectThat(parse<DeliveryConfig>())
          .get { environments.first { it.constraints.isNotEmpty() } }
          .get { constraints.first() }
          .isA<DependsOnConstraint>()
      }
    }
  }

  data class Fixture(val json: String) {
    val mapper = configuredObjectMapper().apply {
      registerSubtypes(NamedType(DependsOnConstraint::class.java, "depends-on"))
      registerSubtypes(NamedType(ManualJudgementConstraint::class.java, "manual-judgment"))
    }

    inline fun <reified T> parse(): T = mapper.readValue<T>(json)
  }
}
