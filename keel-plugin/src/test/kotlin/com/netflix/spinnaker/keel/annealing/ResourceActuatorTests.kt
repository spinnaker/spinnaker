package com.netflix.spinnaker.keel.annealing

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceMetadata
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceDeltaDetected
import com.netflix.spinnaker.keel.events.ResourceMissing
import com.netflix.spinnaker.keel.events.TaskRef
import com.netflix.spinnaker.keel.persistence.ResourceState.Diff
import com.netflix.spinnaker.keel.persistence.ResourceState.Missing
import com.netflix.spinnaker.keel.persistence.ResourceState.Ok
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.plugin.ResolvableResourceHandler
import com.netflix.spinnaker.keel.plugin.ResolvedResource
import com.netflix.spinnaker.keel.telemetry.ResourceChecked
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isA
import java.time.Clock

internal object ResourceActuatorTests : JUnit5Minutests {

  val resourceRepository = InMemoryResourceRepository()
  val plugin1 = mockk<ResolvableResourceHandler<DummyResourceSpec, DummyResource>>(relaxUnitFun = true)
  val plugin2 = mockk<ResolvableResourceHandler<DummyResourceSpec, DummyResource>>(relaxUnitFun = true)
  val publisher = mockk<ApplicationEventPublisher>(relaxUnitFun = true)

  fun tests() = rootContext<ResourceActuator> {

    fixture {
      ResourceActuator(resourceRepository, listOf(plugin1, plugin2), publisher, Clock.systemDefaultZone())
    }

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
        metadata = ResourceMetadata(
          name = ResourceName("resource1"),
          uid = randomUID()
        ),
        spec = DummyResourceSpec("whatever")
      )

      before {
        resourceRepository.store(resource)
        resourceRepository.appendHistory(ResourceCreated(resource))
      }

      test("before the actuator checks the resource the only event in its history is creation") {
        expectThat(resourceRepository.eventHistory(resource.metadata.uid))
          .hasSize(1)
          .first()
          .isA<ResourceCreated>()
      }

      context("the current state matches the desired state") {
        before {
          every {
            plugin1.resolve(resource)
          } returns ResolvedResource(
            desired = DummyResource(resource.spec.state),
            current = DummyResource(resource.spec.state)
          )

          with(resource) {
            checkResource(metadata.name, apiVersion, kind)
          }
        }

        test("the resource is not updated") {
          verify(exactly = 0) { plugin1.create(any(), any()) }
          verify(exactly = 0) { plugin1.update(any(), any()) }
          verify(exactly = 0) { plugin1.delete(any()) }
        }

        test("only the relevant plugin is queried") {
          verify(exactly = 0) { plugin2.resolve(any()) }
        }

        test("nothing is added to the resource history") {
          expectThat(resourceRepository.eventHistory(resource.metadata.uid))
            .hasSize(1)
        }

        test("a telemetry event is published") {
          // TODO: this is wrong
          verify { publisher.publishEvent(ResourceChecked(resource, Ok)) }
        }
      }

      context("the current state is missing") {
        before {
          every {
            plugin1.resolve(resource)
          } returns ResolvedResource(
            desired = DummyResource(resource.spec.state),
            current = null
          )
          every { plugin1.create(resource, any()) } returns listOf(TaskRef("/tasks/${randomUID()}"))

          with(resource) {
            checkResource(metadata.name, apiVersion, kind)
          }
        }

        test("the resource is created via the relevant handler") {
          verify { plugin1.create(resource, any()) }
        }

        test("the resource state is recorded") {
          expectThat(resourceRepository.eventHistory(resource.metadata.uid)) {
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
          every {
            plugin1.resolve(resource)
          } returns ResolvedResource(
            DummyResource(resource.spec.state),
            DummyResource("some other state that does not match")
          )
          every { plugin1.update(resource, any()) } returns listOf(TaskRef("/tasks/${randomUID()}"))

          with(resource) {
            checkResource(metadata.name, apiVersion, kind)
          }
        }

        test("the resource is updated") {
          verify { plugin1.update(eq(resource), any()) }
        }

        test("the resource state is recorded") {
          expectThat(resourceRepository.eventHistory(resource.metadata.uid)) {
            hasSize(3)
            first().isA<ResourceActuationLaunched>()
            second().isA<ResourceDeltaDetected>()
          }
        }

        test("a telemetry event is published") {
          verify { publisher.publishEvent(ResourceChecked(resource, Diff)) }
        }
      }
    }
  }
}

private fun <T : Iterable<E>, E> Assertion.Builder<T>.second(): Assertion.Builder<E> =
  get("second element %s") { toList()[1] }

internal data class DummyResource(val state: String)

internal data class DummyResourceSpec(val state: String)
