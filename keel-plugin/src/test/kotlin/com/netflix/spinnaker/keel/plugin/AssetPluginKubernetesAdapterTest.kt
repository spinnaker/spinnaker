package com.netflix.spinnaker.keel.plugin

import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.api.AssetMetadata
import com.netflix.spinnaker.keel.api.AssetName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.k8s.WatchEventType.ADDED
import com.netflix.spinnaker.keel.k8s.WatchEventType.DELETED
import com.netflix.spinnaker.keel.k8s.WatchEventType.MODIFIED
import com.netflix.spinnaker.keel.persistence.AssetRepository
import com.netflix.spinnaker.keel.persistence.ResourceVersionTracker
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import com.oneeyedmen.minutest.junit.JUnit5Minutests
import com.oneeyedmen.minutest.rootContext
import strikt.api.expectThrows
import java.util.*

internal object AssetPluginKubernetesAdapterTest : JUnit5Minutests {

  data class Fixture(
    val plugin: AssetPlugin = mock(),
    val assetRepository: AssetRepository = mock(),
    val resourceVersionTracker: ResourceVersionTracker = mock()
  ) {
    val adapter = AssetPluginKubernetesAdapter(
      assetRepository,
      resourceVersionTracker,
      mock(),
      mock(),
      plugin
    )
  }

  override val tests = rootContext<Fixture> {
    fixture { Fixture() }

    after { reset(plugin, assetRepository, resourceVersionTracker) }

    val asset = Asset(
      SPINNAKER_API_V1,
      "whatever",
      AssetMetadata(
        AssetName("my-resource"),
        1234L,
        UUID.randomUUID()
      ),
      "spec"
    )

    context("a resource is created") {
      context("the plugin handles the asset successfully") {
        before {
          whenever(plugin.create(any())) doReturn ConvergeAccepted

          adapter.onResourceEvent(ADDED, asset)
        }

        test("asset is stored in the repository") {
          verify(assetRepository).store(asset)
        }

        test("asset is passed to the plugin") {
          verify(plugin).create(asset)
        }

        test("resource version is updated") {
          verify(resourceVersionTracker).set(asset.metadata.resourceVersion!!)
        }
      }

      context("the plugin does not handle the asset") {
        before {
          whenever(plugin.create(any())) doReturn ConvergeFailed("o noes")

          adapter.onResourceEvent(ADDED, asset)
        }

        test("asset is stored in the repository") {
          verify(assetRepository).store(asset)
        }

        test("asset is passed to the plugin") {
          verify(plugin).create(asset)
        }

        test("resource version is not updated") {
          verify(resourceVersionTracker, never()).set(any())
        }
      }

      context("the plugin throws an exception") {
        before {
          whenever(plugin.create(any())) doThrow RuntimeException("o noes")

          expectThrows<RuntimeException> {
            adapter.onResourceEvent(ADDED, asset)
          }
        }

        test("asset is stored in the repository") {
          verify(assetRepository).store(asset)
        }

        test("asset is passed to the plugin") {
          verify(plugin).create(asset)
        }

        test("resource version is not updated") {
          verify(resourceVersionTracker, never()).set(any())
        }
      }
    }

    context("a resource is updated") {
      context("the plugin handles the asset successfully") {
        before {
          whenever(plugin.update(any())) doReturn ConvergeAccepted

          adapter.onResourceEvent(MODIFIED, asset)
        }

        test("asset is stored in the repository") {
          verify(assetRepository).store(asset)
        }

        test("asset is passed to the plugin") {
          verify(plugin).update(asset)
        }

        test("resource version is updated") {
          verify(resourceVersionTracker).set(asset.metadata.resourceVersion!!)
        }
      }

      context("the plugin does not handle the asset") {
        before {
          whenever(plugin.update(any())) doReturn ConvergeFailed("o noes")

          adapter.onResourceEvent(MODIFIED, asset)
        }

        test("asset is stored in the repository") {
          verify(assetRepository).store(asset)
        }

        test("asset is passed to the plugin") {
          verify(plugin).update(asset)
        }

        test("resource version is not updated") {
          verify(resourceVersionTracker, never()).set(any())
        }
      }

      context("the plugin throws an exception") {
        before {
          whenever(plugin.update(any())) doThrow RuntimeException("o noes")

          expectThrows<RuntimeException> {
            adapter.onResourceEvent(MODIFIED, asset)
          }
        }

        test("asset is stored in the repository") {
          verify(assetRepository).store(asset)
        }

        test("asset is passed to the plugin") {
          verify(plugin).update(asset)
        }

        test("resource version is not updated") {
          verify(resourceVersionTracker, never()).set(any())
        }
      }
    }

    context("a resource is deleted") {
      context("the plugin handles the asset successfully") {
        before {
          whenever(plugin.delete(any())) doReturn ConvergeAccepted

          adapter.onResourceEvent(DELETED, asset)
        }

        test("asset is deleted from the repository") {
          verify(assetRepository).delete(asset.metadata.name)
        }

        test("asset is passed to the plugin") {
          verify(plugin).delete(asset)
        }

        test("resource version is updated") {
          verify(resourceVersionTracker).set(asset.metadata.resourceVersion!!)
        }
      }

      context("the plugin does not handle the asset") {
        before {
          whenever(plugin.delete(any())) doReturn ConvergeFailed("o noes")

          adapter.onResourceEvent(DELETED, asset)
        }

        test("asset is not deleted from the repository") {
          verify(assetRepository, never()).delete(asset.metadata.name)
        }

        test("asset is passed to the plugin") {
          verify(plugin).delete(asset)
        }

        test("resource version is not updated") {
          verify(resourceVersionTracker, never()).set(any())
        }
      }

      context("the plugin throws an exception") {
        before {
          whenever(plugin.delete(any())) doThrow RuntimeException("o noes")

          expectThrows<RuntimeException> {
            adapter.onResourceEvent(DELETED, asset)
          }
        }

        test("asset is not deleted from the repository") {
          verify(assetRepository, never()).delete(asset.metadata.name)
        }

        test("asset is passed to the plugin") {
          verify(plugin).delete(asset)
        }

        test("resource version is not updated") {
          verify(resourceVersionTracker, never()).set(any())
        }
      }
    }
  }
}
