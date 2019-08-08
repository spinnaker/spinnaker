package com.netflix.spinnaker.keel.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.persistence.randomData
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.jackson.has
import strikt.jackson.path
import strikt.jackson.textValue
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

internal class ResourceEventSerializationTests : JUnit5Minutests {

  data class Fixture(
    val mapper: ObjectMapper = configuredObjectMapper(),
    val resource: Resource<Any>,
    val clock: Clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
  )

  val Fixture.createdEvent: ResourceCreated
    get() = ResourceCreated(resource, clock)

  val Fixture.json: String
    get() = """
      {
        "type": "${createdEvent.javaClass.simpleName}",
        "uid": "${createdEvent.uid}",
        "apiVersion": "${createdEvent.apiVersion}",
        "kind": "${createdEvent.kind}",
        "name": "${createdEvent.name}",
        "timestamp": "${createdEvent.timestamp}"
      }
    """.trimIndent()

  val Fixture.yaml: String
    get() = """
      --- !<${createdEvent.javaClass.simpleName}>
      uid: "${createdEvent.uid}"
      apiVersion: "${createdEvent.apiVersion}"
      kind: "${createdEvent.kind}"
      name: "${createdEvent.name}"
      timestamp: "${createdEvent.timestamp}"
    """.trimIndent()

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture(
        resource = Resource(
          apiVersion = SPINNAKER_API_V1,
          kind = "ec2:whatever",
          metadata = mapOf(
            "uid" to randomUID(),
            "name" to "ec2:prod:ap-south-1:a-thing",
            "serviceAccount" to "keel@spinnaker",
            "application" to "a"
          ),
          spec = randomData()
        )
      )
    }

    context("JSON") {
      test("can serialize a ResourceCreated event") {
        val json = mapper.valueToTree<ObjectNode>(createdEvent)
        expectThat(json)
          .has("uid")
          .has("name")
          .has("apiVersion")
          .has("kind")
          .has("timestamp")
          .has("type")
          .path("type")
          .textValue()
          .isEqualTo(ResourceCreated::class.simpleName)
      }

      test("can deserialize a ResourceCreated event") {
        val event = mapper.readValue<ResourceCreated>(json)
        expectThat(event).isEqualTo(createdEvent)
      }
    }

    context("YAML") {
      deriveFixture {
        copy(mapper = configuredYamlMapper())
      }

      test("can serialize a ResourceCreated event") {
        val json = mapper.valueToTree<ObjectNode>(createdEvent)
        expectThat(json)
          .has("uid")
          .has("name")
          .has("apiVersion")
          .has("kind")
          .has("timestamp")
          .has("type")
          .path("type")
          .textValue()
          .isEqualTo(ResourceCreated::class.simpleName)
      }

      test("can deserialize a ResourceCreated event") {
        val event = mapper.readValue<ResourceCreated>(yaml)
        expectThat(event).isEqualTo(createdEvent)
      }
    }
  }
}
