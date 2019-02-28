package com.netflix.spinnaker.keel.plugin.monitoring

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceMetadata
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import de.huxhorn.sulky.ulid.ULID
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext

internal object ResourceStateMonitorTests : JUnit5Minutests {

  val resourceRepository = InMemoryResourceRepository()
  val plugin1 = mock<ResourceHandler<DummyResource>>()
  val plugin2 = mock<ResourceHandler<DummyResource>>()
  val idGenerator = ULID()

  fun tests() = rootContext<ResourceStateMonitor> {

    fixture {
      ResourceStateMonitor(resourceRepository, listOf(plugin1, plugin2))
    }

    before {
      whenever(plugin1.apiVersion) doReturn SPINNAKER_API_V1.subApi("plugin1")
      whenever(plugin1.supportedKind) doReturn (ResourceKind(SPINNAKER_API_V1.subApi("plugin1").group, "foo", "foos") to DummyResource::class.java)
      whenever(plugin2.apiVersion) doReturn SPINNAKER_API_V1.subApi("plugin2")
      whenever(plugin2.supportedKind) doReturn (ResourceKind(SPINNAKER_API_V1.subApi("plugin2").group, "bar", "bars") to DummyResource::class.java)
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
          uid = idGenerator.nextValue()
        ),
        spec = DummyResource("whatever")
      )

      before {
        resourceRepository.store(resource)
      }

      context("the current state matches the desired state") {
        before {
          whenever(plugin1.current(resource)) doReturn resource.spec

          validateManagedResources()
        }

        test("the resource is not updated") {
          verify(plugin1, never()).create(any())
          verify(plugin1, never()).update(any(), any())
          verify(plugin1, never()).delete(any())
        }

        test("only the relevant plugin is queried") {
          verify(plugin2, never()).current(any())
        }
      }

      context("the current state is missing") {
        before {
          whenever(plugin1.current(resource)) doReturn null as DummyResource?

          validateManagedResources()
        }

        test("the resource is created") {
          verify(plugin1).create(resource)
        }
      }

      context("the current state is wrong") {
        before {
          whenever(plugin1.current(resource)) doReturn DummyResource("some other state that does not match")

          validateManagedResources()
        }

        test("the resource is updated") {
          verify(plugin1).update(eq(resource), any())
        }
      }
    }
  }
}

internal data class DummyResource(val state: String)
