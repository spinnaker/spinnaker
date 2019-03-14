package com.netflix.spinnaker.keel.annealing

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceMetadata
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.persistence.ResourceState.Diff
import com.netflix.spinnaker.keel.persistence.ResourceState.Missing
import com.netflix.spinnaker.keel.persistence.ResourceState.Ok
import com.netflix.spinnaker.keel.persistence.ResourceState.Unknown
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.stub
import com.nhaarman.mockitokotlin2.verify
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.isEqualTo

internal object ResourceActuatorTests : JUnit5Minutests {

  val resourceRepository = InMemoryResourceRepository()
  val plugin1 = mock<ResourceHandler<DummyResource>>()
  val plugin2 = mock<ResourceHandler<DummyResource>>()

  fun tests() = rootContext<ResourceActuator> {

    fixture {
      ResourceActuator(resourceRepository, listOf(plugin1, plugin2))
    }

    before {
      plugin1.stub {
        on { apiVersion } doReturn SPINNAKER_API_V1.subApi("plugin1")
        on { supportedKind } doReturn (ResourceKind(SPINNAKER_API_V1.subApi("plugin1").group, "foo", "foos") to DummyResource::class.java)
      }
      plugin2.stub {
        on { apiVersion } doReturn SPINNAKER_API_V1.subApi("plugin2")
        on { supportedKind } doReturn (ResourceKind(SPINNAKER_API_V1.subApi("plugin2").group, "bar", "bars") to DummyResource::class.java)
      }
    }

    after {
      resourceRepository.dropAll()
      reset(plugin1, plugin2)
    }

    context("a managed resource exists") {
      val resource = Resource(
        apiVersion = SPINNAKER_API_V1.subApi("plugin1"),
        kind = "foo",
        metadata = ResourceMetadata(
          name = ResourceName("resource1"),
          resourceVersion = 1234L,
          uid = randomUID()
        ),
        spec = DummyResource("whatever")
      )

      before {
        resourceRepository.store(resource)
      }

      test("before the actuator checks the resource its status is unknown") {
        expectThat(resourceRepository.lastKnownState(resource.metadata.uid)).first.isEqualTo(Unknown)
      }

      context("the current state matches the desired state") {
        before {
          plugin1.stub {
            on { current(resource) } doReturn resource.spec
          }

          with(resource) {
            checkResource(metadata.name, apiVersion, kind)
          }
        }

        test("the resource is not updated") {
          verify(plugin1, never()).create(any())
          verify(plugin1, never()).update(any(), any())
          verify(plugin1, never()).delete(any())
        }

        test("only the relevant plugin is queried") {
          verify(plugin2, never()).current(any())
        }

        test("the resource state is recorded") {
          expectThat(resourceRepository.lastKnownState(resource.metadata.uid)).first.isEqualTo(Ok)
        }
      }

      context("the current state is missing") {
        before {
          plugin1.stub {
            on { current(resource) } doReturn null as DummyResource?
          }

          with(resource) {
            checkResource(metadata.name, apiVersion, kind)
          }
        }

        test("the resource is created") {
          verify(plugin1).create(resource)
        }

        test("the resource state is recorded") {
          expectThat(resourceRepository.lastKnownState(resource.metadata.uid)).first.isEqualTo(Missing)
        }
      }

      context("the current state is wrong") {
        before {
          plugin1.stub {
            on { current(resource) } doReturn DummyResource("some other state that does not match")
          }

          with(resource) {
            checkResource(metadata.name, apiVersion, kind)
          }
        }

        test("the resource is updated") {
          verify(plugin1).update(eq(resource), any())
        }

        test("the resource state is recorded") {
          expectThat(resourceRepository.lastKnownState(resource.metadata.uid)).first.isEqualTo(Diff)
        }
      }
    }
  }
}

internal data class DummyResource(val state: String)
