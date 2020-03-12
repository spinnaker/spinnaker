package com.netflix.spinnaker.keel.apidocs

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.core.api.SubmittedResource
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import kotlin.reflect.KClass
import org.junit.jupiter.api.extension.ExtendWith
import org.openapi4j.core.validation.ValidationResults
import org.openapi4j.schema.validator.v3.SchemaValidator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import strikt.api.Assertion
import strikt.api.expectThat

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfiguration::class],
  properties = [
    "keel.plugins.bakery.enabled=true",
    "keel.plugins.ec2.enabled=true",
    "keel.plugins.titus.enabled=true"
  ],
  webEnvironment = MOCK
)
@AutoConfigureMockMvc
class ApiDocCompatibilityTests : JUnit5Minutests {
  @Autowired
  lateinit var mvc: MockMvc

  // TODO: can we generate this without having to spin up all the Spring stuff?
  val api: JsonNode by lazy {
    mvc
      .perform(get("/v3/api-docs").accept(APPLICATION_JSON_VALUE))
      .andExpect(status().isOk)
      .andReturn()
      .response
      .contentAsString
      .also(::println)
      .let { jacksonObjectMapper().readTree(it) }
  }

  fun tests() = rootContext<SchemaValidator> {
    fixture {
      SchemaValidator("Keel", api)
    }

    context("resource definitions") {
      mapOf(
        "EC2 cluster" to "/examples/cluster-example.yml",
        "EC2 application load balancer" to "/examples/alb-example.yml",
        "EC2 classic load balancer" to "/examples/clb-example.yml",
        "EC2 cluster with auto-scaling" to "/examples/ec2-cluster-with-autoscaling-example.yml",
        "Bakery image" to "/examples/image-example.yml",
        "EC2 security group" to "/examples/security-group-example.yml",
        "Simple Titus cluster" to "/examples/simple-titus-cluster-example.yml",
        "Titus cluster" to "/examples/titus-cluster-example.yml",
        "Titus cluster with artifact" to "/examples/titus-cluster-with-artifact-example.yml"
      ).forEach { (description, path) ->
        test("$description example is valid") {
          expectThat(validate<SubmittedResource<*>>(path))
            .describedAs(description)
            .isValid()
        }
      }
    }

    context("delivery configs") {
      test("example delivery config is valid") {
        expectThat(validate<SubmittedDeliveryConfig>("/examples/delivery-config-example.yml"))
          .describedAs("example delivery config")
          .isValid()
      }
    }
  }

  private inline fun <reified T> SchemaValidator.validate(path: String): ValidationResults =
    ValidationResults().also {
      validatorFor(T::class).validate(loadExample(path), it)
    }

  /**
   * We need a validator rooted at the node defining the model we're checking, but it also needs
   * the whole schema there as a 'parent' so that `$ref` nodes work properly. Although the docs
   * don't make this at all clear, this seems to be how you achieve that.
   */
  private fun SchemaValidator.validatorFor(type: KClass<*>) =
    SchemaValidator(
      context,
      type.simpleName,
      api.at("/components/schemas/${type.simpleName}"),
      api,
      this
    )

  private fun loadExample(path: String): JsonNode =
    javaClass.getResource(path)?.let { url ->
      YAMLMapper().readTree(url)
    } ?: error("Unable to load resource at $path")
}

fun Assertion.Builder<ValidationResults>.isValid() = assert("is valid") { subject ->
  if (subject.isValid) {
    pass()
  } else {
    fail(subject, "found ${subject.items.size} validation errors: %s")
  }
}
