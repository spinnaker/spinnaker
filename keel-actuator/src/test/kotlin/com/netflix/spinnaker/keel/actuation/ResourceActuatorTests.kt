package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.name
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.api.uid
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceDeltaDetected
import com.netflix.spinnaker.keel.events.ResourceMissing
import com.netflix.spinnaker.keel.events.TaskRef
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.plugin.ResolvableResourceHandler
import com.netflix.spinnaker.keel.telemetry.ResourceCheckSkipped
import com.netflix.spinnaker.keel.telemetry.ResourceChecked
import com.netflix.spinnaker.keel.telemetry.ResourceState.Diff
import com.netflix.spinnaker.keel.telemetry.ResourceState.Error
import com.netflix.spinnaker.keel.telemetry.ResourceState.Missing
import com.netflix.spinnaker.keel.telemetry.ResourceState.Ok
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.springframework.context.ApplicationEventPublisher
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isA
import java.time.Clock

internal class ResourceActuatorTests : JUnit5Minutests {

  class Fixture {
    val resourceRepository = InMemoryResourceRepository()
    val plugin1 = mockk<ResolvableResourceHandler<DummyResourceSpec, DummyResource>>(relaxUnitFun = true)
    val plugin2 = mockk<ResolvableResourceHandler<DummyResourceSpec, DummyResource>>(relaxUnitFun = true)
    val publisher = mockk<ApplicationEventPublisher>(relaxUnitFun = true)
    val subject = ResourceActuator(resourceRepository, listOf(plugin1, plugin2), publisher, Clock.systemDefaultZone())
  }

  fun tests() = rootContext<Fixture> {

    fixture { Fixture() }

    before {
      every { plugin1.name } returns "plugin1"
      every { plugin1.apiVersion } returns SPINNAKER_API_V1.subApi("plugin1")
      every { plugin1.supportedKind } returns (ResourceKind(SPINNAKER_API_V1.subApi("plugin1").group, "foo", "foos") to DummyResourceSpec::class.java)
      every { plugin2.name } returns "plugin2"
      every { plugin2.apiVersion } returns SPINNAKER_API_V1.subApi("plugin2")
      every { plugin2.supportedKind } returns (ResourceKind(SPINNAKER_API_V1.subApi("plugin2").group, "bar", "bars") to DummyResourceSpec::class.java)
    }

    after {
      resourceRepository.dropAll()
    }

    context("a managed resource exists") {
      val resource = Resource(
        apiVersion = SPINNAKER_API_V1.subApi("plugin1"),
        kind = "foo",
        metadata = mapOf(
          "name" to "resource1",
          "uid" to randomUID()
        ),
        spec = DummyResourceSpec("whatever")
      )

      before {
        resourceRepository.store(resource)
        resourceRepository.appendHistory(ResourceCreated(resource))
      }

      test("before the actuator checks the resource the only event in its history is creation") {
        expectThat(resourceRepository.eventHistory(resource.uid))
          .hasSize(1)
          .first()
          .isA<ResourceCreated>()
      }

      context("the plugin is already actuating this resource") {
        before {
          coEvery { plugin1.actuationInProgress(resource.name) } returns true

          with(resource) {
            runBlocking {
              subject.checkResource(name, apiVersion, kind)
            }
          }
        }

        test("the resource is not resolved") {
          coVerify(exactly = 0) { plugin1.desired(any()) }
          coVerify(exactly = 0) { plugin1.current(any()) }
        }

        test("the resource is not updated") {
          coVerify(exactly = 0) { plugin1.create(any(), any()) }
          coVerify(exactly = 0) { plugin1.update(any(), any()) }
          coVerify(exactly = 0) { plugin1.delete(any()) }
        }

        test("nothing is added to the resource history") {
          expectThat(resourceRepository.eventHistory(resource.uid))
            .hasSize(1)
        }

        test("a telemetry event is published") {
          verify { publisher.publishEvent(ResourceCheckSkipped(resource)) }
        }
      }

      context("the plugin is not already actuating this resource") {
        before {
          coEvery { plugin1.actuationInProgress(resource.name) } returns false
        }

        context("the current state matches the desired state") {
          before {
            coEvery {
              plugin1.desired(resource)
            } returns DummyResource(resource.spec.state)
            coEvery {
              plugin1.current(resource)
            } returns DummyResource(resource.spec.state)

            with(resource) {
              runBlocking {
                subject.checkResource(name, apiVersion, kind)
              }
            }
          }

          test("the resource is not updated") {
            coVerify(exactly = 0) { plugin1.create(any(), any()) }
            coVerify(exactly = 0) { plugin1.update(any(), any()) }
            coVerify(exactly = 0) { plugin1.delete(any()) }
          }

          test("only the relevant plugin is queried") {
            coVerify(exactly = 0) { plugin2.desired(any()) }
            coVerify(exactly = 0) { plugin2.current(any()) }
          }

          test("nothing is added to the resource history") {
            expectThat(resourceRepository.eventHistory(resource.uid))
              .hasSize(1)
          }

          test("a telemetry event is published") {
            // TODO: this is wrong
            verify { publisher.publishEvent(ResourceChecked(resource, Ok)) }
          }
        }

        context("the current state is missing") {
          before {
            coEvery { plugin1.desired(resource) } returns DummyResource(resource.spec.state)
            coEvery { plugin1.current(resource) } returns null
            coEvery { plugin1.create(resource, any()) } returns listOf(TaskRef("/tasks/${randomUID()}"))

            with(resource) {
              runBlocking {
                subject.checkResource(name, apiVersion, kind)
              }
            }
          }

          test("the resource is created via the relevant handler") {
            coVerify { plugin1.create(resource, any()) }
          }

          test("the resource state is recorded") {
            expectThat(resourceRepository.eventHistory(resource.uid)) {
              hasSize(3)
              first().isA<ResourceActuationLaunched>()
              second().isA<ResourceMissing>()
            }
          }

          test("a telemetry event is published") {
            verify { publisher.publishEvent(ResourceChecked(resource, Missing)) }
          }
        }

        context("the current state is wrong") {
          before {
            coEvery { plugin1.desired(resource) } returns DummyResource(resource.spec.state)
            coEvery { plugin1.current(resource) } returns DummyResource("some other state that does not match")
            coEvery { plugin1.update(resource, any()) } returns listOf(TaskRef("/tasks/${randomUID()}"))

            with(resource) {
              runBlocking {
                subject.checkResource(name, apiVersion, kind)
              }
            }
          }

          test("the resource is updated") {
            coVerify { plugin1.update(eq(resource), any()) }
          }

          test("the resource state is recorded") {
            expectThat(resourceRepository.eventHistory(resource.uid)) {
              hasSize(3)
              first().isA<ResourceActuationLaunched>()
              second().isA<ResourceDeltaDetected>()
            }
          }

          test("a telemetry event is published") {
            verify { publisher.publishEvent(ResourceChecked(resource, Diff)) }
          }
        }

        context("plugin throws an exception on resource resolution") {
          before {
            coEvery { plugin1.desired(resource) } throws RuntimeException("o noes")
            coEvery { plugin1.current(resource) } returns null

            with(resource) {
              runBlocking {
                subject.checkResource(name, apiVersion, kind)
              }
            }
          }

          test("the resource is not updated") {
            coVerify(exactly = 0) { plugin1.update(any(), any()) }
          }

          // TODO: do we want to track the error in the resource history?
          test("the resource state is not affected") {
            expectThat(resourceRepository.eventHistory(resource.uid)) {
              hasSize(1)
              first().isA<ResourceCreated>()
            }
          }

          test("a telemetry event is published") {
            verify { publisher.publishEvent(ResourceChecked(resource, Error)) }
          }
        }

        context("plugin throws an exception on resource update") {
          before {
            coEvery { plugin1.desired(resource) } returns DummyResource(resource.spec.state)
            coEvery { plugin1.current(resource) } returns DummyResource("some other state that does not match")
            coEvery { plugin1.update(resource, any()) } throws RuntimeException("o noes")

            with(resource) {
              runBlocking {
                subject.checkResource(name, apiVersion, kind)
              }
            }
          }

          // TODO: do we want to track the error in the resource history?
          test("detection of the delta is tracked in resource history") {
            expectThat(resourceRepository.eventHistory(resource.uid)) {
              hasSize(2)
              first().isA<ResourceDeltaDetected>()
              second().isA<ResourceCreated>()
            }
          }

          test("a telemetry event is published") {
            verify { publisher.publishEvent(ResourceChecked(resource, Error)) }
          }
        }
      }
    }
  }
}

private fun <T : Iterable<E>, E> Assertion.Builder<T>.second(): Assertion.Builder<E> =
  get("second element %s") { toList()[1] }

internal data class DummyResource(val state: String)

internal data class DummyResourceSpec(val state: String)
