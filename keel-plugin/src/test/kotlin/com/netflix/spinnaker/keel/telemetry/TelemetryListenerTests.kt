package com.netflix.spinnaker.keel.telemetry

import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Tag
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.persistence.ResourceState.Diff
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import strikt.api.Assertion
import strikt.api.catching
import strikt.api.expectThat
import strikt.assertions.any
import strikt.assertions.isA
import strikt.assertions.isEqualTo

internal object TelemetryListenerTests : JUnit5Minutests {

  private val registry = mockk<Registry>()
  private val counter = mockk<Counter>(relaxUnitFun = true)
  private val event = ResourceChecked(
    apiVersion = SPINNAKER_API_V1.subApi("ec2"),
    kind = "cluster",
    name = ResourceName("ec2:cluster:prod:ap-south-1:keel-main"),
    state = Diff
  )

  fun tests() = rootContext<TelemetryListener> {
    fixture {
      TelemetryListener(registry)
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
            key().isEqualTo("resourceName")
            value().isEqualTo(event.name.value)
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
        every { counter.increment() } throws IllegalStateException("Somebody set up us the bomb")
      }

      test("does not propagate exception") {
        expectThat(catching {
          onResourceChecked(event)
        }).not().isA<Throwable>()

      }
    }
  }
}

fun Assertion.Builder<Tag>.key() = get { key() }
fun Assertion.Builder<Tag>.value() = get { value() }
