package com.netflix.spinnaker.keel.apidocs

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.schema.Generator
import com.netflix.spinnaker.keel.schema.generateSchema
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion.VersionFlag.V201909
import com.networknt.schema.ValidationMessage
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE
import strikt.api.Assertion
import strikt.api.expectThat

@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfiguration::class],
  properties = [
    "keel.plugins.bakery.enabled=true",
    "keel.plugins.ec2.enabled=true",
    "keel.plugins.titus.enabled=true"
  ],
  webEnvironment = NONE
)
@AutoConfigureMockMvc
@EnableAutoConfiguration(exclude = [TaskSchedulingAutoConfiguration::class])
class ApiDocCompatibilityTests {
  @Autowired
  lateinit var generator: Generator

  val schemaFactory: JsonSchemaFactory = JsonSchemaFactory.getInstance(V201909)
  val api by lazy { generator.generateSchema<SubmittedDeliveryConfig>() }
  val schema: JsonSchema by lazy { schemaFactory.getSchema(jacksonObjectMapper().valueToTree<JsonNode>(api)) }

//    context("resource definitions") {
//      mapOf(
//        "EC2 cluster" to "/examples/cluster-example.yml",
//        "EC2 application load balancer" to "/examples/alb-example.yml",
//        "EC2 classic load balancer" to "/examples/clb-example.yml",
//        "EC2 cluster with auto-scaling" to "/examples/ec2-cluster-with-autoscaling-example.yml",
//        "EC2 security group" to "/examples/security-group-example.yml",
//        "Simple Titus cluster" to "/examples/simple-titus-cluster-example.yml",
//        "Titus cluster" to "/examples/titus-cluster-example.yml",
//        "Titus cluster with artifact" to "/examples/titus-cluster-with-artifact-example.yml"
//      ).forEach { (description, path) ->
//        test("$description example is valid") {
//          expectThat(validate(path))
//            .describedAs(description)
//            .isValid()
//        }
//      }
//    }

  @Test
  fun `example delivery config is valid`() {
    val messages = schema.validate(loadExample("/examples/delivery-config-example.yml"))
    expectThat(messages)
      .describedAs("example delivery config")
      .isValid()
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
