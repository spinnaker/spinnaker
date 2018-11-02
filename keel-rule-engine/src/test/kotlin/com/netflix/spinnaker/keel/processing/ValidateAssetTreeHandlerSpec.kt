package com.netflix.spinnaker.keel.processing

import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.api.AssetMetadata
import com.netflix.spinnaker.keel.api.AssetName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.persistence.AssetState.Diff
import com.netflix.spinnaker.keel.persistence.AssetState.Missing
import com.netflix.spinnaker.keel.persistence.AssetState.Ok
import com.netflix.spinnaker.keel.persistence.InMemoryAssetRepository
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
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.second
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

internal object ValidateAssetTreeHandlerSpec : Spek({

  val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
  val repository = InMemoryAssetRepository(clock)
  val assetService: AssetService = mock()
  val queue: Queue = mock()
  val subject = ValidateAssetTreeHandler(repository, assetService, queue)

  val rootAsset = Asset(apiVersion = SPINNAKER_API_V1,
    metadata = AssetMetadata(
      name = AssetName("SecurityGroup:ec2:prod:us-west-2:keel")
    ),
    kind = "SecurityGroup",
    spec = randomData()
  )

  val message = ValidateAsset(rootAsset.id)

  describe("validating an asset") {
    given("no desired state is known") {
      afterGroup {
        repository.dropAll()
        reset(queue)
      }

      on("receiving a message") {
        subject.handle(message)
      }

      it("does not try to converge any assets") {
        verifyZeroInteractions(queue)
      }
    }

    given("assets exist in the repository") {
      beforeGroup {
        repository.store(rootAsset)
      }

      afterGroup {
        repository.dropAll()
      }

      given("all states match the desired state") {
        beforeGroup {
          whenever(assetService.current(rootAsset)) doReturn CurrentAssetPair(rootAsset, rootAsset)
        }

        afterGroup { reset(assetService, queue) }

        on("receiving a message") {
          subject.handle(message)
        }

        it("does not try to converge any assets") {
          verifyZeroInteractions(queue)
        }

        it("marks the asset as $Ok") {
          repository.lastKnownState(rootAsset.id) expect {
            isNotNull().and {
              first.isEqualTo(Ok)
              second.isEqualTo(clock.instant())
            }
          }
        }
      }

      given("the current state of the asset differs from the desired state") {
        beforeGroup {
          whenever(assetService.current(rootAsset)) doReturn CurrentAssetPair(rootAsset, rootAsset.copy(spec = randomData()))
        }

        afterGroup { reset(assetService, queue) }

        on("receiving a message") {
          subject.handle(message)
        }

        it("requests convergence of the invalid asset") {
          verify(queue).push(ConvergeAsset(rootAsset.id))
        }

        it("marks the asset as $Diff") {
          repository.lastKnownState(rootAsset.id) expect {
            isNotNull().and {
              first.isEqualTo(Diff)
              second.isEqualTo(clock.instant())
            }
          }
        }
      }

      given("the asset does not exist in the cloud") {
        beforeGroup {
          whenever(assetService.current(rootAsset)) doReturn CurrentAssetPair(rootAsset, null)
        }

        afterGroup { reset(assetService, queue) }

        on("receiving a message") {
          subject.handle(message)
        }

        it("requests convergence of all missing assets") {
          verify(queue).push(ConvergeAsset(rootAsset.id))
        }

        it("marks missing assets as $Missing") {
          repository.lastKnownState(rootAsset.id) expect {
            isNotNull().and {
              first.isEqualTo(Missing)
              second.isEqualTo(clock.instant())
            }
          }
        }
      }
    }
  }
})

infix fun <T> T.expect(block: Assertion.Builder<T>.() -> Unit) =
  expectThat(this, block)
