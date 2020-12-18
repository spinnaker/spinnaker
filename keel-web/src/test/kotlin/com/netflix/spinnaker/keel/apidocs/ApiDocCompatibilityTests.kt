package com.netflix.spinnaker.keel.apidocs

import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.schema.Generator
import com.netflix.spinnaker.keel.schema.generateSchema
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion.VersionFlag.V201909
import com.networknt.schema.ValidationMessage
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE
import strikt.api.Assertion
import strikt.api.expectThat

@SpringBootTest(
  properties = [
    "keel.plugins.bakery.enabled=true"
  ],
  webEnvironment = NONE
)
class ApiDocCompatibilityTests
@Autowired constructor(val generator: Generator) {

  val schemaFactory: JsonSchemaFactory = JsonSchemaFactory.getInstance(V201909)
  val api by lazy {
    generator.generateSchema<SubmittedDeliveryConfig>()
      .also {
        jacksonObjectMapper()
          .setSerializationInclusion(NON_NULL)
          .enable(INDENT_OUTPUT)
          .writeValueAsString(it)
          .also(::println)
      }
  }
  val schema: JsonSchema by lazy {
    schemaFactory.getSchema(jacksonObjectMapper().valueToTree<JsonNode>(api))
  }

  @TestFactory
  fun validConfigsAreValid(): List<DynamicTest> =
    listOf(
      "/examples/minimal.yml",
      "/examples/cluster-example.yml",
      "/examples/delivery-config-example.yml",
      "/examples/alb-example.yml",
      "/examples/clb-example.yml",
      "/examples/ec2-cluster-with-autoscaling-example.yml",
      "/examples/security-group-example.yml",
      "/examples/security-group-with-cidr-rule-example.yml",
      "/examples/titus-cluster-example.yml",
      "/examples/titus-cluster-with-artifact-example.yml"
    ).map {
      dynamicTest("example delivery config ${it.substringAfterLast("/")} is valid") {
        val messages = schema.validate(loadExample(it))
        expectThat(messages)
          .describedAs("validation messages for ${it.substringAfterLast("/")}")
          .isValid()
      }
    }

  @TestFactory
  fun invalidConfigsAreInvalid(): List<DynamicTest> =
    listOf(
      "/examples/wrong-resource-kind.yml"
    ).map {
      dynamicTest("example delivery config ${it.substringAfterLast("/")} is invalid") {
        val messages = schema.validate(loadExample(it))
        expectThat(messages)
          .describedAs("validation messages for ${it.substringAfterLast("/")}")
          .isNotValid()
      }
    }

  private fun loadExample(path: String): JsonNode =
    javaClass.getResource(path)?.let { url ->
      YAMLMapper().readTree(url)
    } ?: error("Unable to load resource at $path")
}

fun Assertion.Builder<Set<ValidationMessage>>.isValid() = assert("is valid") { subject ->
  if (subject.isEmpty()) {
    pass()
  } else {
    fail(subject, "found ${subject.size} validation errors: %s")
  }
}

fun Assertion.Builder<Set<ValidationMessage>>.isNotValid() = assert("is not valid") { subject ->
  if (subject.isEmpty()) {
    fail(subject, "found no validation errors")
  } else {
    pass()
  }
}
