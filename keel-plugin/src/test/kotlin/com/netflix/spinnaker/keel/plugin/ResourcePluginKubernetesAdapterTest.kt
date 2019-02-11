package com.netflix.spinnaker.keel.plugin

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceMetadata
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.k8s.WatchEventType.ADDED
import com.netflix.spinnaker.keel.k8s.WatchEventType.DELETED
import com.netflix.spinnaker.keel.k8s.WatchEventType.MODIFIED
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.ResourceVersionTracker
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThrows
import java.util.*

internal object ResourcePluginKubernetesAdapterTest : JUnit5Minutests {

  data class Fixture(
    val plugin: ResourcePlugin = mock(),
    val resourceRepository: ResourceRepository = mock(),
    val resourceVersionTracker: ResourceVersionTracker = mock()
  ) {
    val adapter = ResourcePluginKubernetesAdapter(
      resourceRepository,
      resourceVersionTracker,
      mock(),
      mock(),
      plugin
    )
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    after { reset(plugin, resourceRepository, resourceVersionTracker) }

    val resource = Resource(
      SPINNAKER_API_V1,
      "whatever",
      ResourceMetadata(
        ResourceName("my-resource"),
        1234L,
        UUID.randomUUID()
      ),
      "spec"
    )

    context("a resource is created") {
      context("the plugin handles the resource successfully") {
        before {
          whenever(plugin.create(any())) doReturn ConvergeAccepted

          adapter.onResourceEvent(ADDED, resource)
        }

        test("resource is stored in the repository") {
          verify(resourceRepository).store(resource)
        }

        test("resource is passed to the plugin") {
          verify(plugin).create(resource)
        }

        test("resource version is updated") {
          verify(resourceVersionTracker).set(resource.metadata.resourceVersion!!)
        }
      }

      context("the plugin does not handle the resource") {
        before {
          whenever(plugin.create(any())) doReturn ConvergeFailed("o noes")

          adapter.onResourceEvent(ADDED, resource)
        }

        test("resource is stored in the repository") {
          verify(resourceRepository).store(resource)
        }

        test("resource is passed to the plugin") {
          verify(plugin).create(resource)
        }

        test("resource version is not updated") {
          verify(resourceVersionTracker, never()).set(any())
        }
      }

      context("the plugin throws an exception") {
        before {
          whenever(plugin.create(any())) doThrow RuntimeException("o noes")

          expectThrows<RuntimeException> {
            adapter.onResourceEvent(ADDED, resource)
          }
        }

        test("resource is stored in the repository") {
          verify(resourceRepository).store(resource)
        }

        test("resource is passed to the plugin") {
          verify(plugin).create(resource)
        }

        test("resource version is not updated") {
          verify(resourceVersionTracker, never()).set(any())
        }
      }
    }

    context("a resource is updated") {
      context("the plugin handles the resource successfully") {
        before {
          whenever(plugin.update(any())) doReturn ConvergeAccepted

          adapter.onResourceEvent(MODIFIED, resource)
        }

        test("resource is stored in the repository") {
          verify(resourceRepository).store(resource)
        }

        test("resource is passed to the plugin") {
          verify(plugin).update(resource)
        }

        test("resource version is updated") {
          verify(resourceVersionTracker).set(resource.metadata.resourceVersion!!)
        }
      }

      context("the plugin does not handle the resource") {
        before {
          whenever(plugin.update(any())) doReturn ConvergeFailed("o noes")

          adapter.onResourceEvent(MODIFIED, resource)
        }

        test("resource is stored in the repository") {
          verify(resourceRepository).store(resource)
        }

        test("resource is passed to the plugin") {
          verify(plugin).update(resource)
        }

        test("resource version is not updated") {
          verify(resourceVersionTracker, never()).set(any())
        }
      }

      context("the plugin throws an exception") {
        before {
          whenever(plugin.update(any())) doThrow RuntimeException("o noes")

          expectThrows<RuntimeException> {
            adapter.onResourceEvent(MODIFIED, resource)
          }
        }

        test("resource is stored in the repository") {
          verify(resourceRepository).store(resource)
        }

        test("resource is passed to the plugin") {
          verify(plugin).update(resource)
        }

        test("resource version is not updated") {
          verify(resourceVersionTracker, never()).set(any())
        }
      }
    }

    context("a resource is deleted") {
      context("the plugin handles the resource successfully") {
        before {
          whenever(plugin.delete(any())) doReturn ConvergeAccepted

          adapter.onResourceEvent(DELETED, resource)
        }

        test("resource is deleted from the repository") {
          verify(resourceRepository).delete(resource.metadata.name)
        }

        test("resource is passed to the plugin") {
          verify(plugin).delete(resource)
        }

        test("resource version is updated") {
          verify(resourceVersionTracker).set(resource.metadata.resourceVersion!!)
        }
      }

      context("the plugin does not handle the resource") {
        before {
          whenever(plugin.delete(any())) doReturn ConvergeFailed("o noes")

          adapter.onResourceEvent(DELETED, resource)
        }

        test("resource is not deleted from the repository") {
          verify(resourceRepository, never()).delete(resource.metadata.name)
        }

        test("resource is passed to the plugin") {
          verify(plugin).delete(resource)
        }

        test("resource version is not updated") {
          verify(resourceVersionTracker, never()).set(any())
        }
      }

      context("the plugin throws an exception") {
        before {
          whenever(plugin.delete(any())) doThrow RuntimeException("o noes")

          expectThrows<RuntimeException> {
            adapter.onResourceEvent(DELETED, resource)
          }
        }

        test("resource is not deleted from the repository") {
          verify(resourceRepository, never()).delete(resource.metadata.name)
        }

        test("resource is passed to the plugin") {
          verify(plugin).delete(resource)
        }

        test("resource version is not updated") {
          verify(resourceVersionTracker, never()).set(any())
        }
      }
    }
  }
}
