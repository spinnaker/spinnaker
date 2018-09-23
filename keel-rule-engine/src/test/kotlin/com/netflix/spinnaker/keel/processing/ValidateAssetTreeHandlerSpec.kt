package com.netflix.spinnaker.keel.processing

import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.persistence.AssetState.Diff
import com.netflix.spinnaker.keel.persistence.AssetState.Missing
import com.netflix.spinnaker.keel.persistence.AssetState.Ok
import com.netflix.spinnaker.keel.persistence.InMemoryAssetRepository
import com.netflix.spinnaker.q.Queue
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import java.lang.System.nanoTime
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.*

internal object ValidateAssetTreeHandlerSpec : Spek({

  val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
  val repository = InMemoryAssetRepository(clock)
  val assetService: AssetService = mock()
  val queue: Queue = mock()
  val subject = ValidateAssetTreeHandler(repository, assetService, queue)

  val rootAsset = Asset(id = AssetId("SecurityGroup:aws:prod:us-west-2:keel"), kind = "SecurityGroup", spec = randomBytes())
  val assets = listOf(
    Asset(id = AssetId("LoadBalancer:aws:prod:us-west-2:keel"), kind = "LoadBalancer", spec = randomBytes()),
    Asset(id = AssetId("Cluster:aws:prod:us-west-2:keel"), kind = "Cluster", spec = randomBytes())
  ).map {
    it.copy(dependsOn = setOf(rootAsset.id))
  }

  val message = ValidateAssetTree(rootAsset.id)

  describe("validating a sub-tree") {
    given("no desired state is known") {
      beforeGroup {
        assets.forEach(repository::store)
      }

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
        (assets + rootAsset).forEach(repository::store)
      }

      afterGroup {
        repository.dropAll()
      }

      given("all states match the desired state") {
        beforeGroup {
          whenever(assetService.current(rootAsset.wrap())) doReturn CurrentAssetPair(rootAsset, rootAsset)
          for (asset in assets) {
            whenever(assetService.current(asset.wrap())) doReturn CurrentAssetPair(asset, asset)
          }
        }

        afterGroup { reset(assetService, queue) }

        on("receiving a message") {
          subject.handle(message)
        }

        it("does not try to converge any assets") {
          verifyZeroInteractions(queue)
        }

        it("marks all assets as $Ok") {
          (assets + rootAsset).forEach {
            repository.lastKnownState(it.id) expect {
              isNotNull().and {
                chain { it.first }.isEqualTo(Ok)
                chain { it.second }.isEqualTo(clock.instant())
              }
            }
          }
        }
      }

      given("the current state of some assets differ from the desired state") {
        val invalidAssets = (assets.head() + rootAsset)
        val validAssets = assets.tail()

        beforeGroup {
          invalidAssets.forEach {
            val asset = it.copy(spec = randomBytes())
            whenever(assetService.current(it.wrap())) doReturn CurrentAssetPair(asset, asset)
          }
          validAssets.forEach {
            whenever(assetService.current(it.wrap())) doReturn CurrentAssetPair(it, it)
          }
        }

        afterGroup { reset(assetService, queue) }

        on("receiving a message") {
          subject.handle(message)
        }

        it("requests convergence of all invalid assets") {
          invalidAssets.forEach {
            verify(queue).push(ConvergeAsset(it.id))
          }
          verifyNoMoreInteractions(queue)
        }

        it("marks valid assets as $Ok") {
          validAssets.forEach {
            repository.lastKnownState(it.id) expect {
              isNotNull().and {
                chain { it.first }.isEqualTo(Ok)
                chain { it.second }.isEqualTo(clock.instant())
              }
            }
          }
        }

        it("marks invalid assets as $Diff") {
          invalidAssets.forEach {
            repository.lastKnownState(it.id) expect {
              isNotNull().and {
                chain { it.first }.isEqualTo(Diff)
                chain { it.second }.isEqualTo(clock.instant())
              }
            }
          }
        }
      }

      given("some assets do not exist in the cloud") {
        val missingAssets = assets
        val validAssets = setOf(rootAsset)

        beforeGroup {
          missingAssets.forEach {
            whenever(assetService.current(it.wrap())) doReturn CurrentAssetPair(it, null)
          }
          validAssets.forEach {
            whenever(assetService.current(it.wrap())) doReturn CurrentAssetPair(it, it)
          }
        }

        afterGroup { reset(assetService, queue) }

        on("receiving a message") {
          subject.handle(message)
        }

        it("requests convergence of all missing assets") {
          missingAssets.forEach {
            verify(queue).push(ConvergeAsset(it.id))
          }
          verifyNoMoreInteractions(queue)
        }

        it("marks valid assets as $Ok") {
          validAssets.forEach {
            repository.lastKnownState(it.id) expect {
              isNotNull().and {
                chain { it.first }.isEqualTo(Ok)
                chain { it.second }.isEqualTo(clock.instant())
              }
            }
          }
        }

        it("marks missing assets as $Missing") {
          missingAssets.forEach {
            repository.lastKnownState(it.id) expect {
              isNotNull().and {
                chain { it.first }.isEqualTo(Missing)
                chain { it.second }.isEqualTo(clock.instant())
              }
            }
          }
        }
      }
    }
  }
})

fun randomBytes(length: Int = 20) =
  ByteArray(length).also(Random(nanoTime())::nextBytes)

infix fun <T> T.expect(block: Assertion.Builder<T>.() -> Unit) =
  expectThat(this, block)

fun <T> Iterable<T>.head(): List<T> = listOf(first())
fun <T> Iterable<T>.tail(): List<T> = drop(1)
