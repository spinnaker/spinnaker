package com.netflix.spinnaker.keel.plugin.monitoring

import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.api.AssetKind
import com.netflix.spinnaker.keel.api.AssetMetadata
import com.netflix.spinnaker.keel.api.AssetName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.persistence.memory.InMemoryAssetRepository
import com.netflix.spinnaker.keel.plugin.AssetPlugin
import com.netflix.spinnaker.keel.plugin.ResourceMissing
import com.netflix.spinnaker.keel.plugin.ResourceState
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import com.oneeyedmen.minutest.junit.JUnit5Minutests
import com.oneeyedmen.minutest.rootContext
import java.util.*

internal object AssetStateMonitorTests : JUnit5Minutests {

  val assetRepository = InMemoryAssetRepository()
  val plugin1 = mock<AssetPlugin>()
  val plugin2 = mock<AssetPlugin>()

  override val tests = rootContext<AssetStateMonitor> {

    fixture {
      AssetStateMonitor(assetRepository, listOf(plugin1, plugin2))
    }

    before {
      whenever(plugin1.apiVersion) doReturn SPINNAKER_API_V1.subApi("plugin1")
      whenever(plugin1.supportedKinds) doReturn mapOf(AssetKind(SPINNAKER_API_V1.subApi("plugin1").group, "foo", "foos") to String::class.java)
      whenever(plugin2.apiVersion) doReturn SPINNAKER_API_V1.subApi("plugin2")
      whenever(plugin2.supportedKinds) doReturn mapOf(AssetKind(SPINNAKER_API_V1.subApi("plugin2").group, "bar", "bars") to String::class.java, AssetKind(SPINNAKER_API_V1.subApi("plugin2").group, "baz", "bazzes") to String::class.java)
    }

    after {
      assetRepository.dropAll()
      reset(plugin1, plugin2)
    }

    context("a managed asset exists") {
      val asset = Asset(
        apiVersion = SPINNAKER_API_V1.subApi("plugin1"),
        kind = "foo",
        metadata = AssetMetadata(
          name = AssetName("asset1"),
          resourceVersion = 1234L,
          uid = UUID.randomUUID()
        ),
        spec = "whatever"
      )

      before {
        assetRepository.store(asset)
      }

      context("the current state matches the desired state") {
        before {
          whenever(plugin1.current(asset)) doReturn ResourceState(asset.spec)

          validateManagedAssets()
        }

        test("the asset is not updated") {
          verify(plugin1, never()).create(any())
          verify(plugin1, never()).update(any())
          verify(plugin1, never()).delete(any())
        }

        test("only the relevant plugin is queried") {
          verify(plugin2, never()).current(any())
        }
      }

      context("the current state is missing") {
        before {
          whenever(plugin1.current(asset)) doReturn ResourceMissing

          validateManagedAssets()
        }

        test("the asset is created") {
          verify(plugin1).create(asset)
        }
      }

      context("the current state is wrong") {
        before {
          whenever(plugin1.current(asset)) doReturn ResourceState("some other state that does not match")

          validateManagedAssets()
        }

        test("the asset is created") {
          verify(plugin1).update(asset)
        }
      }
    }

  }
}
