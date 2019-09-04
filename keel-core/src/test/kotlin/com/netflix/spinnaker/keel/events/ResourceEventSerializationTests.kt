package com.netflix.spinnaker.keel.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import com.netflix.spinnaker.keel.test.resource
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
    val resource: Resource<*>,
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
        "id": "${createdEvent.id}",
        "application": "${createdEvent.application}",
        "timestamp": "${createdEvent.timestamp}"
      }
    """.trimIndent()

  val Fixture.yaml: String
    get() = """
      --- !<${createdEvent.javaClass.simpleName}>
      uid: "${createdEvent.uid}"
      apiVersion: "${createdEvent.apiVersion}"
      kind: "${createdEvent.kind}"
      id: "${createdEvent.id}"
      application: "${createdEvent.application}"
      timestamp: "${createdEvent.timestamp}"
    """.trimIndent()

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture(
        resource = resource()
      )
    }

    context("JSON") {
      test("can serialize a ResourceCreated event") {
        val json = mapper.valueToTree<ObjectNode>(createdEvent)
        expectThat(json)
          .has("uid")
          .has("id")
          .has("apiVersion")
          .has("kind")
          .has("application")
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
          .has("id")
          .has("apiVersion")
          .has("kind")
          .has("application")
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
