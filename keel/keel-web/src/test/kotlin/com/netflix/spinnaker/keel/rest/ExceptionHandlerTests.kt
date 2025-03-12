package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.jsontype.NamedType
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import com.netflix.spinnaker.keel.test.DummyResourceHandlerV1
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.TEST_API_V1
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expect
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.size

class ExceptionHandlerTests : JUnit5Minutests {
  class Fixture(
    brokenYaml: String
  ) {
    val subject = ExceptionHandler(listOf(DummyResourceHandlerV1))
    val mapper = configuredYamlMapper()
    val parseException = try {
      mapper.registerSubtypes(NamedType(DummyResourceSpec::class.java, TEST_API_V1.qualify("whatever").toString()))
      mapper.readValue(brokenYaml, SubmittedDeliveryConfig::class.java)
      throw IllegalArgumentException("test is broken")
    } catch (e: JsonMappingException) {
      // we wrap it to emulate the HttpMessageConversionException passed to ExceptionHandler
      Exception(e)
    }
    val apiError = subject.onParseFailure(parseException)
  }

  fun tests() = rootContext<Fixture> {
    context("delivery config missing required top-level field") {
      fixture {
        Fixture(
          """
            ---
            name: fnord
            # application: fnord
            serviceAccount: keel@netlix.com
            artifacts: []
            environments:
            - name: test
              constraints: []
              notifications: []
              resources:
              - kind: "${TEST_API_V1.qualify("whatever")}"
                spec: {}
          """.trimIndent()
        )
      }

      test("should cause an ApiError with the expected details") {
        expect {
          that(apiError.details) {
            isA<ParsingErrorDetails>().and {
              get { error }.isEqualTo(ParsingError.MISSING_PROPERTY)
              get { path }.size.isEqualTo(1)
              get { pathExpression }.isEqualTo(".application")
            }
          }
        }
      }
    }

    context("delivery config with invalid top-level field") {
      fixture {
        Fixture(
          """
            ---
            name: fnord
            application: fnord
            serviceAccount: keel@netlix.com
            artifacts: true # wrong
            environments:
            - name: test
              constraints: []
              notifications: []
              resources:
              - kind: "${TEST_API_V1.qualify("whatever")}"
                spec: {}
          """.trimIndent()
        )
      }

      test("should cause an ApiError with the expected details") {
        expect {
          that(apiError.details) {
            isA<ParsingErrorDetails>().and {
              get { error }.isEqualTo(ParsingError.INVALID_TYPE)
              get { path }.size.isEqualTo(1)
              get { pathExpression }.isEqualTo(".artifacts")
            }
          }
        }
      }
    }

    context("environment with missing required field") {
      fixture {
        Fixture(
          """
            ---
            name: fnord
            application: fnord
            serviceAccount: keel@netlix.com
            artifacts: []
            environments:
            - constraints: []
              notifications: []
              resources:
              - kind: "${TEST_API_V1.qualify("whatever")}"
                spec: {}
          """.trimIndent()
        )
      }

      test("should cause an ApiError with the expected details") {
        expect {
          that(apiError.details) {
            isA<ParsingErrorDetails>().and {
              get { error }.isEqualTo(ParsingError.INVALID_VALUE)
              get { path }.size.isEqualTo(2)
              get { pathExpression }.isEqualTo(".environments[0]")
            }
          }
        }
      }
    }

    context("environment with invalid type for field") {
      fixture {
        Fixture(
          """
            ---
            name: fnord
            application: fnord
            serviceAccount: keel@netlix.com
            artifacts: []
            environments:
            - name: test
              constraints: true # wrong
              notifications: []
              resources:
              - kind: "${TEST_API_V1.qualify("whatever")}"
                spec: {}
          """.trimIndent()
        )
      }

      test("should cause an ApiError with the expected details") {
        expect {
          that(apiError.details) {
            isA<ParsingErrorDetails>().and {
              get { error }.isEqualTo(ParsingError.INVALID_TYPE)
              get { path }.size.isEqualTo(3)
              get { pathExpression }.isEqualTo(".environments[0].constraints")
            }
          }
        }
      }
    }

    context("resource missing field checked by validation") {
      fixture {
        Fixture(
          """
            ---
            name: fnord
            application: fnord
            serviceAccount: keel@netlix.com
            artifacts: []
            environments:
            - name: test
              constraints: []
              notifications: []
              resources:
              - spec: {}
                # kind: "${TEST_API_V1.qualify("whatever")}"
          """.trimIndent()
        )
      }

      test("should cause an ApiError with the expected details") {
        expect {
          that(apiError.details) {
            isA<ParsingErrorDetails>().and {
              get { error }.isEqualTo(ParsingError.INVALID_TYPE)
              get { path }.size.isEqualTo(5)
              get { pathExpression }.isEqualTo(".environments[0].resources[0].spec")
            }
          }
        }
      }
    }

    context("resource with invalid type for field") {
      fixture {
        Fixture(
          """
            ---
            name: fnord
            application: fnord
            serviceAccount: keel@netlix.com
            artifacts: []
            environments:
            - name: test
              constraints: []
              notifications: []
              resources:
              - kind: "${TEST_API_V1.qualify("whatever")}"
                spec:
                  intData: "wrong"
          """.trimIndent()
        )
      }

      test("should cause an ApiError with the expected details") {
        expect {
          that(apiError.details) {
            isA<ParsingErrorDetails>().and {
              get { error }.isEqualTo(ParsingError.INVALID_TYPE)
              get { path }.size.isEqualTo(5)
              get { pathExpression }.isEqualTo(".environments[0].resources[0].intData")
            }
          }
        }
      }
    }

    context("resource with invalid format for field") {
      fixture {
        Fixture(
          """
            ---
            name: fnord
            application: fnord
            serviceAccount: keel@netlix.com
            artifacts: []
            environments:
            - name: test
              constraints: []
              notifications: []
              resources:
              - kind: "${TEST_API_V1.qualify("whatever")}"
                spec:
                  timeData: "wrong"
          """.trimIndent()
        )
      }

      test("should cause an ApiError with the expected details") {
        expect {
          that(apiError.details) {
            isA<ParsingErrorDetails>().and {
              get { error }.isEqualTo(ParsingError.INVALID_FORMAT)
              get { path }.size.isEqualTo(5)
              get { pathExpression }.isEqualTo(".environments[0].resources[0].timeData")
            }
          }
        }
      }
    }

    context("resource with invalid value for enum field") {
      fixture {
        Fixture(
          """
            ---
            name: fnord
            application: fnord
            serviceAccount: keel@netlix.com
            artifacts: []
            environments:
            - name: test
              constraints: []
              notifications: []
              resources:
              - kind: "${TEST_API_V1.qualify("whatever")}"
                spec:
                  enumData: "wrong"
          """.trimIndent()
        )
      }

      test("should cause an ApiError with the expected details") {
        expect {
          that(apiError.details) {
            isA<ParsingErrorDetails>().and {
              get { error }.isEqualTo(ParsingError.INVALID_TYPE)
              get { path }.size.isEqualTo(5)
              get { pathExpression }.isEqualTo(".environments[0].resources[0].enumData")
            }
          }
        }
      }
    }
  }
}
