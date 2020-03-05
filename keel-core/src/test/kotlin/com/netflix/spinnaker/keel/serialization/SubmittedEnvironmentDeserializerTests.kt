package com.netflix.spinnaker.keel.serialization

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.jsontype.NamedType
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.ResourceKind.Companion.parseKind
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.core.api.SubmittedEnvironment
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isA
import strikt.assertions.isEqualTo

class SubmittedEnvironmentDeserializerTests : JUnit5Minutests {

  data class Fixture(
    val json: String
  ) {
    val mapper = configuredYamlMapper()
      .apply {
        configure(DeserializationFeature.WRAP_EXCEPTIONS, true)
        registerSubtypes(NamedType(TestSubnetAwareLocatableResource::class.java, parseKind("test/test-subnet-aware-locatable@v1").toString()))
        registerSubtypes(NamedType(TestSimpleLocatableResource::class.java, parseKind("test/test-simple-locatable@v1").toString()))
      }
  }

  fun tests() = rootContext<Fixture> {
    context("locations is specified on the resource spec") {
      fixture {
        Fixture("""
          |---
          |name: test
          |resources:
          |- kind: test/test-subnet-aware-locatable@v1
          |  metadata:
          |    serviceAccount: mickey@disney.com
          |  spec:
          |    id: my-resource
          |    application: whatever
          |    locations:
          |      account: prod
          |      subnet: internal (vpc0)
          |      vpc: vpc0
          |      regions:
          |      - name: us-east-1
          |      - name: us-west-2
          |locations:
          |  account: test
          |  subnet: internal (vpc0)
          |  vpc: vpc0
          |  regions:
          |  - name: us-east-1
          |  - name: us-west-2
          """.trimMargin()
        )
      }

      test("resource's locations corresponds to what was specified in the resource spec") {
        val environment = mapper.readValue<SubmittedEnvironment>(json)

        expectThat(environment.resources.first().spec)
          .isA<TestSubnetAwareLocatableResource>()
          .get { locations.account }
          .isEqualTo("prod")
      }
    }

    context("locations is omitted from the resource spec") {
      fixture {
        Fixture("""
          |---
          |name: test
          |resources:
          |- kind: test/test-subnet-aware-locatable@v1
          |  metadata:
          |    serviceAccount: mickey@disney.com
          |  spec:
          |    id: my-resource
          |    application: whatever
          |locations:
          |  account: test
          |  subnet: internal (vpc0)
          |  vpc: vpc0
          |  regions:
          |  - name: us-east-1
          |  - name: us-west-2
          """.trimMargin()
        )
      }

      test("locations corresponds to what was specified at the environment level") {
        val environment = mapper.readValue<SubmittedEnvironment>(json)

        expectThat(environment.resources.first().spec)
          .isA<TestSubnetAwareLocatableResource>()
          .get { locations.account }
          .isEqualTo("test")
      }
    }

    context("locations is omitted from the resource spec for a resource with non-subnet aware locations") {
      fixture {
        Fixture("""
          |---
          |name: test
          |resources:
          |- kind: test/test-simple-locatable@v1
          |  metadata:
          |    serviceAccount: mickey@disney.com
          |  spec:
          |    id: my-resource
          |    application: whatever
          |locations:
          |  account: test
          |  subnet: internal (vpc0)
          |  vpc: vpc0
          |  regions:
          |  - name: us-east-1
          |  - name: us-west-2
          """.trimMargin()
        )
      }

      test("locations corresponds to what was specified at the environment level") {
        val environment = mapper.readValue<SubmittedEnvironment>(json)

        expectThat(environment.resources.first().spec)
          .isA<TestSimpleLocatableResource>()
          .get { locations.account }
          .isEqualTo("test")
      }
    }

    context("locations appears in one environment but not another") {
      fixture {
        Fixture("""
          |---
          |- name: test
          |  resources:
          |  - kind: test/test-subnet-aware-locatable@v1
          |    metadata:
          |      serviceAccount: mickey@disney.com
          |    spec:
          |      id: my-test-resource
          |      application: whatever
          |  locations:
          |    account: test
          |    subnet: internal (vpc0)
          |    vpc: vpc0
          |    regions:
          |    - name: us-east-1
          |    - name: us-west-2
          |- name: prod
          |  resources:
          |  - kind: test/test-subnet-aware-locatable@v1
          |    metadata:
          |      serviceAccount: mickey@disney.com
          |    spec:
          |      id: my-prod-resource
          |      application: whatever
          """.trimMargin()
        )
      }

      test("locations does not leak from one environment to another") {
        expectThrows<JsonProcessingException> {
          mapper.readValue<List<SubmittedEnvironment>>(json)
        }
      }
    }

    context("locations is omitted from the resource spec and the environment") {
      fixture {
        Fixture("""
          |---
          |name: test
          |resources:
          |- kind: test/test-locatable@v1
          |  metadata:
          |    serviceAccount: mickey@disney.com
          |  spec:
          |    id: my-resource
          |    application: whatever
          """.trimMargin()
        )
      }

      test("the resource cannot be parsed") {
        expectThrows<JsonProcessingException> {
          mapper.readValue<SubmittedEnvironment>(json)
        }
      }
    }
  }
}

private data class TestSubnetAwareLocatableResource(
  override val id: String,
  override val application: String,
  override val locations: SubnetAwareLocations
) : Locatable<SubnetAwareLocations>

private data class TestSimpleLocatableResource(
  override val id: String,
  override val application: String,
  override val locations: SimpleLocations
) : Locatable<SimpleLocations>
