package com.netflix.spinnaker.keel.telemetry

import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Tag
import com.netflix.spinnaker.keel.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.events.ResourceValid
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import org.junit.jupiter.api.fail
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.any
import strikt.assertions.isEqualTo

internal class TelemetryListenerTests : JUnit5Minutests {

  private val registry = spyk<Registry>(NoopRegistry())
  private val counter = mockk<Counter>(relaxUnitFun = true)
  private val event = ResourceValid(
    apiVersion = "ec2.$SPINNAKER_API_V1",
    kind = "cluster",
    id = "ec2:cluster:prod:keel-main",
    application = "fnord",
    timestamp = Instant.now()
  )

  fun tests() = rootContext<TelemetryListener> {
    fixture {
      TelemetryListener(registry, Clock.systemDefaultZone())
    }

    before {
      every { registry.counter(any(), any<List<Tag>>()) } returns counter
    }

    context("successful metric submission") {
      before {
        onResourceChecked(event)
      }

      test("increments an Atlas counter") {
        verify {
          counter.increment()
        }
      }

      test("tags the counter") {
        val id = slot<String>()
        val tags = slot<List<Tag>>()
        verify {
          registry.counter(capture(id), capture(tags))
        }
        expectThat(id.captured).isEqualTo("keel.resource.checked")
        expectThat(tags.captured) {
          any {
            key().isEqualTo("apiVersion")
            value().isEqualTo(event.apiVersion.toString())
          }
          any {
            key().isEqualTo("resourceKind")
            value().isEqualTo(event.kind)
          }
          any {
            key().isEqualTo("resourceId")
            value().isEqualTo(event.id)
          }
          any {
            key().isEqualTo("resourceApplication")
            value().isEqualTo(event.application)
          }
          any {
            key().isEqualTo("resourceState")
            value().isEqualTo(event.state.name)
          }
        }
      }
    }

    context("metric submission fails") {
      before {
        every { counter.id() } returns NoopRegistry().createId("whatever")
        every { counter.increment() } throws IllegalStateException("Somebody set up us the bomb")
      }

      test("does not propagate exception") {
        try {
          onResourceChecked(event)
        } catch (ex: Exception) {
          fail { "Did not expect an exception but caught: ${ex.message}" }
        }
      }
    }
  }
}

fun Assertion.Builder<Tag>.key() = get { key() }
fun Assertion.Builder<Tag>.value() = get { value() }
