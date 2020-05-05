package com.netflix.spinnaker.keel.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.netflix.spinnaker.keel.core.ResourceCurrentlyUnresolvable
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.kork.exceptions.SpinnakerException
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import java.time.Clock
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.jackson.has
import strikt.jackson.path
import strikt.jackson.textValue

internal class ResourceEventSerializationTests : JUnit5Minutests {
  companion object {
    val clock = Clock.systemUTC()
    val resource = resource()

    // Map of events to additional properties required to deserialize
    val events = mapOf(
      ResourceCreated(resource, clock) to
        emptyMap(),
      ResourceUpdated(resource, emptyMap(), clock) to
        mapOf("delta" to emptyMap<String, Any?>()),
      ResourceDeleted(resource, clock) to
        emptyMap(),
      ResourceMissing(resource, clock) to
        emptyMap(),
      ResourceActuationLaunched(resource, "plugin", emptyList(), clock) to
        mapOf("plugin" to "plugin", "tasks" to emptyList<String>()),
      ResourceDeltaDetected(resource, emptyMap(), clock) to
        mapOf("delta" to emptyMap<String, Any?>()),
      ResourceDeltaResolved(resource, clock) to
        emptyMap(),
      ResourceValid(resource, clock) to
        emptyMap(),
      ResourceCheckError(resource, SpinnakerException("oops!"), clock) to
        mapOf("exceptionType" to SpinnakerException::class.java, "exceptionMessage" to "oops!"),
      ResourceCheckUnresolvable(resource, object : ResourceCurrentlyUnresolvable("oops!") {}, clock) to
        emptyMap(),
      ResourceActuationPaused(resource, clock) to
        emptyMap(),
      ResourceActuationResumed(resource, clock) to
        emptyMap(),
      ResourceActuationVetoed(resource, "vetoed", clock) to
        mapOf("reason" to "vetoed"),
      ResourceTaskFailed(resource, "failed", emptyList(), clock) to
        mapOf("reason" to "failed"),
      ResourceTaskSucceeded(resource, emptyList(), clock) to
        emptyMap()
    )
  }

  data class Fixture(
    val mapper: ObjectMapper,
    val event: ResourceEvent
  ) {
    private val commonProperties = mapOf(
      "type" to event.javaClass.simpleName,
      "kind" to event.kind,
      "id" to event.id,
      "application" to event.application,
      "timestamp" to event.timestamp,
      "message" to event.message
    )

    private val additionalProperties = events[event]!!

    fun serialized(): String =
      mapper.writeValueAsString(commonProperties + additionalProperties)
  }

  fun tests() = rootContext<Fixture> {
    events.keys.forEach { event ->
      context("${event.javaClass.simpleName} - JSON") {
        fixture {
          Fixture(mapper = configuredObjectMapper(), event = event)
        }

        test("can serialize a ${event.javaClass.simpleName} event") {
          val json = mapper.valueToTree<ObjectNode>(event)
          expectThat(json)
            .has("id")
            .has("kind")
            .has("application")
            .has("timestamp")
            .has("type")
            .path("type")
            .textValue()
            .isEqualTo(event.javaClass.simpleName)
        }

        test("can deserialize a ${event.javaClass.simpleName} event") {
          val deserialized = mapper.readValue(serialized(), event.javaClass)
          expectThat(deserialized).isEqualTo(event)
        }
      }

      context("${event.javaClass.simpleName} - YAML") {
        fixture {
          Fixture(mapper = configuredYamlMapper(), event = event)
        }

        test("can serialize a ${event.javaClass.simpleName} event") {
          val json = mapper.valueToTree<ObjectNode>(event)
          expectThat(json)
            .has("id")
            .has("kind")
            .has("application")
            .has("timestamp")
            .has("type")
            .path("type")
            .textValue()
            .isEqualTo(event.javaClass.simpleName)
        }

        test("can deserialize a ${event.javaClass.simpleName} event") {
          val deserialized = mapper.readValue(serialized(), event.javaClass)
          expectThat(deserialized).isEqualTo(event)
        }
      }
    }
  }
}
