package com.netflix.spinnaker.keel.apidocs

import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.MissingNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.api.Constraint
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.VersionedArtifactProvider
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule
import com.netflix.spinnaker.keel.api.support.ExtensionRegistry
import com.netflix.spinnaker.keel.api.support.extensionsOf
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.docker.ContainerProvider
import com.netflix.spinnaker.keel.schema.Generator
import com.netflix.spinnaker.keel.schema.generateSchema
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import dev.minutest.experimental.SKIP
import dev.minutest.experimental.minus
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.doesNotContain
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue
import strikt.assertions.one
import strikt.jackson.at
import strikt.jackson.booleanValue
import strikt.jackson.findValuesAsText
import strikt.jackson.has
import strikt.jackson.isArray
import strikt.jackson.isMissing
import strikt.jackson.isObject
import strikt.jackson.isTextual
import strikt.jackson.path
import strikt.jackson.textValue
import strikt.jackson.textValues
import kotlin.reflect.KClass

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
class ApiDocTests : JUnit5Minutests {

  @Autowired
  lateinit var generator: Generator

  @Autowired
  lateinit var extensionRegistry: ExtensionRegistry

  val resourceSpecTypes
    get() = extensionRegistry.extensionsOf<ResourceSpec>()

  val constraintTypes
    get() = extensionRegistry.extensionsOf<Constraint>().values.toList()

  val securityGroupRuleTypes
    get() = SecurityGroupRule::class.sealedSubclasses.map(KClass<*>::java)

  val containerProviderTypes
    get() = ContainerProvider::class.sealedSubclasses.map(KClass<*>::java)

  val artifactTypes
    get() = extensionRegistry.extensionsOf<DeliveryArtifact>().values.toList()

  fun tests() = rootContext<Assertion.Builder<JsonNode>> {
    fixture {
      val api = generator.generateSchema<SubmittedDeliveryConfig>()
        .also {
          jacksonObjectMapper()
            .setSerializationInclusion(NON_NULL)
            .enable(INDENT_OUTPUT)
            .writeValueAsString(it)
            .also(::println)
        }
        .let { jacksonObjectMapper().valueToTree<JsonNode>(it) }
      expectThat(api).describedAs("API Docs response")
    }

    test("Does not contain a schema for ResourceKind") {
      at("/\$defs/ResourceKind")
        .isMissing()
    }

    test("SubmittedResource kind is defined as one of the possible resource kinds") {
      at("/\$defs/SubmittedResource/properties/kind/enum")
        .isArray()
        .textValues()
        .containsExactlyInAnyOrder(extensionRegistry.extensionsOf<ResourceSpec>().keys)
    }

    resourceSpecTypes
      .mapValues { it.value.simpleName }
      .forEach { (kind, specSubType) ->
        test("contains a sub-schema for SubmittedResource predicated on a kind of $kind") {
          at("/\$defs/SubmittedResource/allOf")
            .isArray()
            .one {
              path("if")
                .path("properties")
                .path("kind")
                .path("const")
                .textValue()
                .isEqualTo(kind)
            }
        }

        test("contains a sub-schema for SubmittedResource with a spec of $specSubType") {
          at("/\$defs/SubmittedResource/allOf")
            .isArray()
            .one {
              path("then")
                .path("properties")
                .path("spec")
                .isObject()
                .path("\$ref")
                .textValue()
                .isEqualTo("#/\$defs/${specSubType}")
            }
        }
      }

    test("contains a schema for Constraint with all sub-types") {
      at("/\$defs/Constraint")
        .isObject()
        .path("oneOf")
        .isArray()
        .findValuesAsText("\$ref")
        .containsExactlyInAnyOrder(
          "#/\$defs/CanaryConstraint",
          "#/\$defs/DependsOnConstraint",
          "#/\$defs/ManualJudgementConstraint",
          "#/\$defs/PipelineConstraint",
          "#/\$defs/TimeWindowConstraint",
          "#/\$defs/ArtifactUsedConstraint",
          "#/\$defs/ImageExistsConstraint"
        )
    }

    constraintTypes
      .map(Class<*>::getSimpleName)
      .forEach { type ->
        test("Constraint sub-type $type has its own schema") {
          at("/\$defs/$type")
            .isObject()
        }
      }

    test("contains a schema for DeliveryArtifact with all sub-types") {
      at("/\$defs/DeliveryArtifact")
        .isObject()
        .path("oneOf")
        .isArray()
        .findValuesAsText("\$ref")
        .containsExactlyInAnyOrder(artifactTypes.map { "#/\$defs/${it.simpleName}" })
    }

    sequenceOf(
      DebianArtifact::class,
      DockerArtifact::class
    )
      .map(KClass<*>::simpleName)
      .forEach { type ->
        test("DeliveryArtifact sub-type $type has its own schema") {
          at("/\$defs/$type")
            .isObject()
        }
      }

    securityGroupRuleTypes.map(Class<*>::getSimpleName)
      .forEach { type ->
        test("SecurityGroupRule sub-type $type has its own schema") {
          at("/\$defs/$type")
            .isObject()
        }
      }

    test("schema for SecurityGroupRule is oneOf the sub-types") {
      at("/\$defs/SecurityGroupRule")
        .isObject()
        .has("oneOf")
        .path("oneOf")
        .isArray()
        .findValuesAsText("\$ref")
        .containsExactlyInAnyOrder(
          securityGroupRuleTypes.map { "#/\$defs/${it.simpleName}" }
        )
    }

    containerProviderTypes.map(Class<*>::getSimpleName)
      .forEach { type ->
        test("ContainerProvider sub-type $type has its own schema") {
          at("/\$defs/$type")
            .isObject()
        }
      }

    test("schema for ContainerProvider is oneOf the sub-types") {
      at("/\$defs/ContainerProvider")
        .isObject()
        .has("oneOf")
        .path("oneOf")
        .isArray()
        .findValuesAsText("\$ref")
        .containsExactlyInAnyOrder(
          containerProviderTypes.map { "#/\$defs/${it.simpleName}" }
        )
    }

    test("schemas for DeliveryArtifact sub-types specify the fixed discriminator value") {
      at("/\$defs/DebianArtifact/properties/type/const")
        .textValue()
        .isEqualTo("deb")
      at("/\$defs/DockerArtifact/properties/type/const")
        .textValue()
        .isEqualTo("docker")
    }

    test("data class parameters without default values are required") {
      at("/\$defs/SubmittedResource/required")
        .isArray()
        .textValues()
        .containsExactlyInAnyOrder("kind")
    }

    test("data class parameters with default values are not required") {
      at("/\$defs/SubmittedResource/required")
        .isArray()
        .textValues()
        .doesNotContain("metadata")
    }

    test("nullable data class parameters without default values are not required") {
      at("/\$defs/SecurityGroupSpec/required")
        .isArray()
        .textValues()
        .doesNotContain("description")
    }

    test("prefers @JsonCreator properties to default constructor") {
      at("/\$defs/ClusterSpec/required")
        .isArray()
        .textValues()
        .containsExactly("moniker")
    }

    test("duration properties are references to the duration schema") {
      at("/\$defs/RedBlack/properties/delayBeforeDisable/\$ref")
        .textValue()
        .isEqualTo("#/\$defs/Duration")
    }

    test("duration schema is a patterned string") {
      at("/\$defs/Duration")
        .and {
          path("type").textValue().isEqualTo("string")
          path("pattern").isTextual()
        }
    }

    test("non-nullable properties are marked as non-nullable in the schema") {
      at("/\$defs/Moniker/properties/app/nullable")
        .booleanValue()
        .isFalse()
    }

    test("a class annotated with @Description can have a description") {
      at("/description")
        .isTextual()
    }

    SKIP - test("annotated class description is inherited") {
      at("/\$defs/ClusterSpecSubmittedResource/description")
        .isTextual()
    }

    test("a property annotated with @Description can have a description") {
      at("/properties/serviceAccount/description")
        .isTextual()
    }

    SKIP - test("annotated property description is inherited") {
      at("/\$defs/ClusterSpecSubmittedResource/properties/spec/description")
        .isTextual()
    }

    test("IngressPorts are either an enum or an object") {
      at("/\$defs/IngressPorts/oneOf")
        .isArray()
        .one {
          path("\$ref").textValue().isEqualTo("#/\$defs/PortRange")
        }
        .one {
          path("const").isTextual().textValue().isEqualTo("ALL")
        }
    }

    resourceSpecTypes
      .values
      .filter { Locatable::class.java.isAssignableFrom(it) }
      .map(Class<*>::getSimpleName)
      .forEach { locatableType ->
        test("locations property of $locatableType is optional") {
          at("/\$defs/$locatableType/required")
            .isArray()
            .doesNotContain("locations")
        }
      }

    test("property with type Map<String, Any?> does not restrict the value type to object") {
      at("/\$defs/SubmittedResource/properties/metadata/additionalProperties")
        .isA<BooleanNode>()
        .booleanValue()
        .isTrue()
    }

    listOf(
      VersionedArtifactProvider::artifactName.name,
      VersionedArtifactProvider::artifactType.name,
      VersionedArtifactProvider::artifactVersion.name,
    ).forEach { propertyName ->
      test("ClusterSpec does not contain transient property $propertyName used for image resolution") {
        at("/\$defs/ClusterSpec/properties/$propertyName")
          .isA<MissingNode>()
      }

      test("TitusClusterSpec does not contain transient property $propertyName used for image resolution") {
        at("/\$defs/TitusClusterSpec/properties/$propertyName")
          .isA<MissingNode>()
      }
    }
  }
}
