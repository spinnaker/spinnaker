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
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.time.Instant.now

internal object ConvergeAssetHandlerSpec : Spek({

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

  describe("converging an asset") {
    given("the asset cannot be found") {
      beforeGroup { whenever(repository.get(asset.id)) doReturn null as Asset? }
      afterGroup { reset(repository) }

      on("receiving a message") {
        subject.handle(message)
      }

      // TODO: do we want to flag an error? I feel like yes.
      it("does nothing") {
        verifyZeroInteractions(assetService)
      }
    }

    given("dependent assets are up-to-date") {
      beforeGroup {
        whenever(repository.get(asset.id)) doReturn asset
        whenever(repository.lastKnownState(asset.id)) doReturn (Ok to now())
      }

      afterGroup { reset(repository) }

      given("nothing vetoes the convergence") {
        beforeGroup { whenever(vetoService.allow(asset)) doReturn true }
        afterGroup { reset(vetoService) }

        on("receiving a message") {
          subject.handle(message)
        }

        it("requests convergence of the asset") {
          verify(assetService).converge(asset)
        }
      }

      given("something vetoes the convergence") {
        beforeGroup { whenever(vetoService.allow(asset)) doReturn false }
        afterGroup { reset(vetoService) }

        on("receiving a message") {
          subject.handle(message)
        }

        it("does not request convergence of the asset") {
          verifyZeroInteractions(assetService)
        }
      }
    }
  }
})
