package com.netflix.spinnaker.keel.apidocs

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.api.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupSpec
import com.netflix.spinnaker.keel.api.titus.cluster.TitusClusterSpec
import com.netflix.spinnaker.keel.bakery.api.ImageSpec
import com.netflix.spinnaker.keel.core.api.CanaryConstraint
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.core.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.core.api.PipelineConstraint
import com.netflix.spinnaker.keel.core.api.SubmittedResource
import com.netflix.spinnaker.keel.core.api.TimeWindowConstraint
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.swagger.v3.core.util.RefUtils.constructRef
import kotlin.reflect.KClass
import org.junit.jupiter.api.extension.ExtendWith
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
import strikt.assertions.contains
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.doesNotContain
import strikt.assertions.exactly
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue
import strikt.assertions.map
import strikt.jackson.at
import strikt.jackson.booleanValue
import strikt.jackson.has
import strikt.jackson.isArray
import strikt.jackson.isMissing
import strikt.jackson.isObject
import strikt.jackson.isTextual
import strikt.jackson.path
import strikt.jackson.textValue

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
class ApiDocTests : JUnit5Minutests {
  @Autowired
  lateinit var mvc: MockMvc

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

  fun tests() = rootContext<Assertion.Builder<JsonNode>> {
    fixture {
      expectThat(api).describedAs("API Docs response")
    }

    /**
     * Ensures that [GenericWildcardTypeModelConverter] is being used for all cases of things that
     * contain `ResourceSpec`.
     */
    test("schema for Resource's spec property is a reference to ResourceSpec") {
      at("/components/schemas/Resource/properties/spec/\$ref")
        .textValue()
        .isEqualTo(constructRef("ResourceSpec"))
    }

    sequenceOf(Resource::class, SubmittedResource::class)
      .map(KClass<*>::simpleName)
      .forEach { type ->

        test("does not contain parameterized type versions of schema for $type") {
          at("/components/schemas/${type}Object").isMissing()
          at("/components/schemas/${type}ResourceSpec").isMissing()
        }
      }

    test("contains a schema for ResourceSpec with all sub-types") {
      at("/components/schemas/ResourceSpec")
        .isObject()
        .path("oneOf")
        .isArray()
        .findValuesAsText("\$ref")
        .containsExactlyInAnyOrder(
          constructRef("ApplicationLoadBalancerSpec"),
          constructRef("ClassicLoadBalancerSpec"),
          constructRef("ClusterSpec"),
          constructRef("ImageSpec"),
          constructRef("SecurityGroupSpec"),
          constructRef("TitusClusterSpec")
        )
    }

    sequenceOf(
      ApplicationLoadBalancerSpec::class,
      ClassicLoadBalancerSpec::class,
      ClusterSpec::class,
      ImageSpec::class,
      SecurityGroupSpec::class,
      TitusClusterSpec::class
    )
      .map(KClass<*>::simpleName)
      .forEach { type ->
        test("ResourceSpec sub-type $type has its own schema") {
          at("/components/schemas/$type")
            .isObject()
        }
      }

    test("contains a schema for Constraint with all sub-types") {
      at("/components/schemas/Constraint")
        .isObject()
        .path("oneOf")
        .isArray()
        .findValuesAsText("\$ref")
        .containsExactlyInAnyOrder(
          constructRef("CanaryConstraint"),
          constructRef("DependsOnConstraint"),
          constructRef("ManualJudgementConstraint"),
          constructRef("PipelineConstraint"),
          constructRef("TimeWindowConstraint")
        )
    }

    sequenceOf(
      CanaryConstraint::class,
      DependsOnConstraint::class,
      ManualJudgementConstraint::class,
      PipelineConstraint::class,
      TimeWindowConstraint::class
    )
      .map(KClass<*>::simpleName)
      .forEach { type ->
        test("Constraint sub-type $type has its own schema") {
          at("/components/schemas/$type")
            .isObject()
        }
      }

    test("contains a schema for DeliveryArtifact with all sub-types") {
      at("/components/schemas/DeliveryArtifact")
        .isObject()
        .path("oneOf")
        .isArray()
        .findValuesAsText("\$ref")
        .containsExactlyInAnyOrder(
          constructRef("DebianArtifact"),
          constructRef("DockerArtifact")
        )
    }

    sequenceOf(
      DebianArtifact::class,
      DockerArtifact::class
    )
      .map(KClass<*>::simpleName)
      .forEach { type ->
        test("DeliveryArtifact sub-type $type has its own schema") {
          at("/components/schemas/$type")
            .isObject()
        }
      }

    test("schema for a sealed class is oneOf the sub-types") {
      at("/components/schemas/ImageProvider")
        .isObject()
        .has("oneOf")
        .path("oneOf")
        .isArray()
        .findValuesAsText("\$ref")
        .containsExactlyInAnyOrder(
          constructRef("ArtifactImageProvider"),
          constructRef("JenkinsImageProvider"),
          constructRef("ReferenceArtifactImageProvider")
        )
    }

    test("does not include interim sealed classes in oneOf") {
      at("/components/schemas/ResourceEvent/oneOf")
        .isArray()
        .findValuesAsText("\$ref")
        .doesNotContain(constructRef("ResourceCheckResult"))
    }

    test("does not create schemas for interim sealed classes") {
      at("/components/schemas/ResourceCheckResult")
        .isMissing()
    }

    test("data class parameters without default values are required") {
      at("/components/schemas/SubmittedResource/required")
        .isArray()
        .textValues()
        .contains("apiVersion", "kind", "spec")
    }

    test("data class parameters with default values are not required") {
      at("/components/schemas/SubmittedResource/required")
        .isArray()
        .textValues()
        .doesNotContain("metadata")
    }

    test("nullable data class parameters with default values are not required") {
      at("/components/schemas/SecurityGroupSpec/required")
        .isArray()
        .textValues()
        .doesNotContain("description")
    }

    test("prefers @JsonCreator properties to default constructor") {
      at("/components/schemas/ClusterSpec/required")
        .isArray()
        .textValues()
        .containsExactlyInAnyOrder("imageProvider", "moniker")
    }

    test("duration properties are duration format strings") {
      at("/components/schemas/RedBlack/properties/delayBeforeDisable")
        .and {
          path("type").textValue().isEqualTo("string")
          path("format").textValue().isEqualTo("duration")
          path("properties").isMissing()
        }
    }

    test("instant properties are date-time format strings") {
      at("/components/schemas/ResourceCreated/properties/timestamp")
        .and {
          path("type").textValue().isEqualTo("string")
          path("format").textValue().isEqualTo("date-time")
          path("properties").isMissing()
        }
    }

    test("non-nullable properties are marked as non-nullable in the schema") {
      at("/components/schemas/Moniker/properties/app/nullable")
        .booleanValue()
        .isFalse()
    }

    test("nullable properties are marked as nullable in the schema") {
      at("/components/schemas/Moniker/properties/stack/nullable")
        .booleanValue()
        .isTrue()
    }

    test("a class annotated with @Description can have a description") {
      at("/components/schemas/SubmittedResource/description")
        .isTextual()
    }

    test("a property annotated with @Description can have a description") {
      at("/components/schemas/SubmittedResource/properties/spec/allOf")
        .isArray()
        .one {
          path("description").isTextual()
        }
    }

    test("property required-ness can be overridden with the @Optional annotation") {
      at("/components/schemas/SubmittedResource/properties/spec/allOf")
        .isArray()
        .one {
          path("description").isTextual()
        }
    }
  }
}

// TODO: move to strikt.jackson
private fun Assertion.Builder<ArrayNode>.findValuesAsText(fieldName: String): Assertion.Builder<Iterable<String>> =
  get { findValuesAsText(fieldName) }

// TODO: move to strikt.jackson
private fun Assertion.Builder<ArrayNode>.textValues(): Assertion.Builder<Iterable<String>> =
  map { it.textValue() }

// TODO: move to strikt.core
infix fun <T : Iterable<E>, E> Assertion.Builder<T>.one(predicate: Assertion.Builder<E>.() -> Unit): Assertion.Builder<T> =
  exactly(1, predicate)
