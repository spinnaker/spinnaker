package com.netflix.spinnaker.keel.processing

import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.api.AssetMetadata
import com.netflix.spinnaker.keel.api.AssetName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.persistence.AssetRepository
import com.netflix.spinnaker.keel.persistence.AssetState.Ok
import com.netflix.spinnaker.keel.persistence.randomData
import com.netflix.spinnaker.q.Queue
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import com.oneeyedmen.minutest.junit.junitTests
import org.junit.jupiter.api.TestFactory
import java.time.Instant.now

internal object ConvergeAssetHandlerTests {

  val repository: AssetRepository = mock()
  val assetService: AssetService = mock()
  val vetoService: VetoService = mock()
  val queue: Queue = mock()
  val subject = ConvergeAssetHandler(repository, queue, assetService, vetoService)

  val asset = Asset(
    apiVersion = SPINNAKER_API_V1,
    metadata = AssetMetadata(
      name = AssetName("Cluster:ec2:prod:us-west-2:keel")
    ),
    kind = "ec2.Cluster",
    spec = randomData()
  )

  val message = ConvergeAsset(asset.id)

  @TestFactory
  fun `converging an asset`() = junitTests<Unit> {
    context("the asset cannot be found") {
      before { whenever(repository.get(asset.id)) doReturn null as Asset<*>? }
      after { reset(repository) }

      // TODO: do we want to flag an error? I feel like yes.
      test("on receiving a message it does nothing") {
        subject.handle(message)

        verifyZeroInteractions(assetService)
      }
    }

    context("dependent assets are up-to-date") {
      before {
        whenever(repository.get(asset.id)) doReturn asset
        whenever(repository.lastKnownState(asset.id)) doReturn (Ok to now())
      }

      after { reset(repository) }

      context("nothing vetoes the convergence") {
        before { whenever(vetoService.allow(asset)) doReturn true }
        after { reset(vetoService) }

        test("on receiving a message it requests convergence of the asset") {
          subject.handle(message)

          verify(assetService).converge(asset)
        }
      }

      context("something vetoes the convergence") {
        before { whenever(vetoService.allow(asset)) doReturn false }
        after { reset(vetoService) }

        test("on receiving a message it does not request convergence of the asset") {
          subject.handle(message)

          verifyZeroInteractions(assetService)
        }
      }
    }
  }
}
