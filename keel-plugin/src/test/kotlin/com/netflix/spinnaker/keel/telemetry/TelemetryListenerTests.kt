package com.netflix.spinnaker.keel.telemetry

import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Tag
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.info.InstanceIdSupplier
import com.netflix.spinnaker.keel.persistence.ResourceState
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
import java.util.UUID.randomUUID

internal object TelemetryListenerTests : JUnit5Minutests {

  private val registry = mockk<Registry>()
  private val counter = mockk<Counter>(relaxUnitFun = true)
  private val instanceId = "i-${randomUUID()}"
  private val event = ResourceChecked(
    ResourceName("ec2:cluster:prod:ap-south-1:keel-main"),
    ResourceState.Diff
  )

  fun tests() = rootContext<TelemetryListener> {
    fixture {
      TelemetryListener(
        registry,
        object : InstanceIdSupplier {
          override fun get() = instanceId
        }
      )
    }

    before {
      every { registry.counter(any<Id>()) } returns counter
    }

    context("succesful metric submission") {
      before {
        onResourceChecked(event)
      }

      test("increments an Atlas counter") {
        verify(timeout = 100) {
          counter.increment()
        }
      }

      test("tags the counter") {
        val id = slot<Id>()
        verify(timeout = 100) {
          registry.counter(capture(id))
        }
        expectThat(id.captured) {
          name().isEqualTo("keel.resource.checked")
          tags()
            .any {
              key().isEqualTo("resource_name")
              value().isEqualTo(event.name.value)
            }
            .any {
              key().isEqualTo("resource_state")
              value().isEqualTo(event.state.name)
            }
            .any {
              key().isEqualTo("instance_id")
              value().isEqualTo(instanceId)
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

fun Assertion.Builder<Id>.name() = get { name() }
fun Assertion.Builder<Id>.tags() = get { tags() }
fun Assertion.Builder<Tag>.key() = get { key() }
fun Assertion.Builder<Tag>.value() = get { value() }
