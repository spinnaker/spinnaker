package com.netflix.spinnaker.keel.processing

import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.persistence.AssetState.Diff
import com.netflix.spinnaker.keel.persistence.AssetState.Missing
import com.netflix.spinnaker.keel.persistence.AssetState.Ok
import com.netflix.spinnaker.keel.persistence.InMemoryAssetRepository
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import strikt.api.Assertion
import strikt.api.expect
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import java.lang.System.nanoTime
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.*

internal object AssetValidatorSpec : Spek({

  val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
  val repository = InMemoryAssetRepository(clock)
  val assetService: AssetService = mock()
  val subject = AssetValidator(repository, assetService)

  val rootAsset = Asset(id = AssetId("SecurityGroup:aws:prod:us-west-2:keel"), kind = "SecurityGroup", spec = randomBytes())
  val assets = listOf(
    Asset(id = AssetId("LoadBalancer:aws:prod:us-west-2:keel"), kind = "LoadBalancer", spec = randomBytes()),
    Asset(id = AssetId("Cluster:aws:prod:us-west-2:keel"), kind = "Cluster", spec = randomBytes())
  ).map {
    it.copy(dependsOn = setOf(rootAsset.id))
  }

  describe("validating a sub-tree") {
    given("no desired state is known") {
      beforeGroup {
        assets.forEach(repository::store)
      }

      afterGroup {
        repository.dropAll()
      }

      it("returns an empty set") {
        subject.validateSubTree(rootAsset.id) expect {
          isEmpty()
        }
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
          whenever(assetService.current(rootAsset)) doReturn rootAsset
          for (asset in assets) {
            whenever(assetService.current(asset)) doReturn asset
          }
        }

        afterGroup { reset(assetService) }

        it("returns an empty set") {
          subject.validateSubTree(rootAsset.id) expect {
            isEmpty()
          }
        }

        it("marks all assets as ${Ok::class.java.simpleName}") {
          (assets + rootAsset).forEach {
            repository.lastKnownState(it.id) expect {
              isNotNull().and {
                map { it.first }.isEqualTo(Ok)
                map { it.second }.isEqualTo(clock.instant())
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
            whenever(assetService.current(it)) doReturn it.copy(spec = randomBytes())
          }
          validAssets.forEach {
            whenever(assetService.current(it)) doReturn it
          }
        }

        afterGroup { reset(assetService) }

        it("returns all invalid asset ids") {
          subject.validateSubTree(rootAsset.id) expect {
            containsExactlyInAnyOrder(*invalidAssets.map { it.id }.toTypedArray())
          }
        }

        it("marks valid assets as ${Ok::class.java.simpleName}") {
          validAssets.forEach {
            repository.lastKnownState(it.id) expect {
              isNotNull().and {
                map { it.first }.isEqualTo(Ok)
                map { it.second }.isEqualTo(clock.instant())
              }
            }
          }
        }

        it("marks invalid assets as ${Diff::class.java.simpleName}") {
          invalidAssets.forEach {
            repository.lastKnownState(it.id) expect {
              isNotNull().and {
                map { it.first }.isEqualTo(Diff)
                map { it.second }.isEqualTo(clock.instant())
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
            whenever(assetService.current(it)) doReturn null as Asset?
          }
          validAssets.forEach {
            whenever(assetService.current(it)) doReturn it
          }
        }

        afterGroup { reset(assetService) }

        it("returns all invalid asset ids") {
          subject.validateSubTree(rootAsset.id) expect {
            containsExactlyInAnyOrder(*missingAssets.map { it.id }.toTypedArray())
          }
        }

        it("marks valid assets as ${Ok::class.java.simpleName}") {
          validAssets.forEach {
            repository.lastKnownState(it.id) expect {
              isNotNull().and {
                map { it.first }.isEqualTo(Ok)
                map { it.second }.isEqualTo(clock.instant())
              }
            }
          }
        }

        it("marks missing assets as ${Missing::class.java.simpleName}") {
          missingAssets.forEach {
            repository.lastKnownState(it.id) expect {
              isNotNull().and {
                map { it.first }.isEqualTo(Missing)
                map { it.second }.isEqualTo(clock.instant())
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
  expect(this, block)

fun <T> Iterable<T>.head(): List<T> = listOf(first())
fun <T> Iterable<T>.tail(): List<T> = drop(1)
