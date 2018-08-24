package com.netflix.spinnaker.keel.processing

import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetContainer
import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.persistence.AssetRepository
import com.netflix.spinnaker.keel.persistence.AssetState.Diff
import com.netflix.spinnaker.keel.persistence.AssetState.Missing
import com.netflix.spinnaker.keel.persistence.AssetState.Ok
import com.netflix.spinnaker.keel.persistence.AssetState.Unknown
import com.netflix.spinnaker.q.Queue
import com.nhaarman.mockito_kotlin.any
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

  val level2Dependency = Asset(
    id = AssetId("SecurityGroup:ec2:prod:us-west-2:keel"),
    kind = "SecurityGroup",
    spec = randomBytes()
  )
  val level1Dependency = Asset(
    id = AssetId("LoadBalancer:ec2:prod:us-west-2:keel"),
    kind = "LoadBalancer",
    dependsOn = setOf(level2Dependency.id),
    spec = randomBytes()
  )
  val asset = Asset(
    id = AssetId("Cluster:ec2:prod:us-west-2:keel"),
    kind = "Cluster",
    dependsOn = setOf(level1Dependency.id),
    spec = randomBytes()
  )

  val message = ConvergeAsset(asset.id)

  describe("converging an asset") {
    given("the asset cannot be found") {
      beforeGroup { whenever(repository.getContainer(asset.id)) doReturn null as AssetContainer? }
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
        whenever(repository.getContainer(asset.id)) doReturn asset.wrap()
        setOf(asset, level1Dependency, level2Dependency).forEach {
          whenever(repository.get(it.id)) doReturn it
        }
        whenever(repository.lastKnownState(any())) doReturn (Ok to now())
      }

      afterGroup { reset(repository) }

      given("nothing vetoes the convergence") {
        beforeGroup { whenever(vetoService.allow(asset.wrap())) doReturn true }
        afterGroup { reset(vetoService) }

        on("receiving a message") {
          subject.handle(message)
        }

        it("requests convergence of the asset") {
          verify(assetService).converge(asset.wrap())
        }
      }

      given("something vetoes the convergence") {
        beforeGroup { whenever(vetoService.allow(asset.wrap())) doReturn false }
        afterGroup { reset(vetoService) }

        on("receiving a message") {
          subject.handle(message)
        }

        it("does not request convergence of the asset") {
          verifyZeroInteractions(assetService)
        }
      }
    }

    sequenceOf(Diff, Missing, Unknown).forEach { state ->
      given("a direct dependency is in $state state") {
        beforeGroup {
          whenever(repository.getContainer(asset.id)) doReturn asset.wrap()
          setOf(asset, level1Dependency, level2Dependency).forEach {
            whenever(repository.get(it.id)) doReturn it
          }
          whenever(repository.lastKnownState(level2Dependency.id)) doReturn (Ok to now())
          whenever(repository.lastKnownState(level1Dependency.id)) doReturn (state to now())
          whenever(repository.lastKnownState(asset.id)) doReturn (Ok to now())
        }

        afterGroup { reset(repository) }

        on("receiving a message") {
          subject.handle(message)
        }

        it("does not request convergence of the asset") {
          verifyZeroInteractions(assetService)
        }

        // TODO: only necessary because Mockito defaults return to false. Ultimately won't just use boolean so this test won't be needed
        it("does not check to see if asset may be converged") {
          verifyZeroInteractions(vetoService)
        }
      }

      given("an indirect dependency is in $state state") {
        beforeGroup {
          whenever(repository.getContainer(asset.id)) doReturn asset.wrap()
          setOf(asset, level1Dependency, level2Dependency).forEach {
            whenever(repository.get(it.id)) doReturn it
          }
          whenever(repository.lastKnownState(level2Dependency.id)) doReturn (state to now())
          whenever(repository.lastKnownState(level1Dependency.id)) doReturn (Ok to now())
          whenever(repository.lastKnownState(asset.id)) doReturn (Ok to now())
        }

        afterGroup { reset(repository) }

        on("receiving a message") {
          subject.handle(message)
        }

        it("does not request convergence of the asset") {
          verifyZeroInteractions(assetService)
        }

        // TODO: only necessary because Mockito defaults return to false. Ultimately won't just use boolean so this test won't be needed
        it("does not check to see if asset may be converged") {
          verifyZeroInteractions(vetoService)
        }
      }
    }
  }
})
