package com.netflix.spinnaker.keel.processing

import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetId
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
import java.lang.System.nanoTime
import java.util.*

internal object AssetProcessorSpec : Spek({

  val repository = InMemoryAssetRepository()
  val assetService: AssetService = mock()
  val subject = AssetProcessor(repository, assetService)

  val rootAsset = Asset(id = AssetId("SecurityGroup:aws:prod:us-west-2:keel"), kind = "SecurityGroup", spec = randomBytes())
  val assets = listOf(
    Asset(id = AssetId("LoadBalancer:aws:prod:us-west-2:keel"), kind = "LoadBalancer", spec = randomBytes()),
    Asset(id = AssetId("Cluster:aws:prod:us-west-2:keel"), kind = "Cluster", spec = randomBytes())
  ).map {
    it.copy(dependsOn = setOf(rootAsset.id))
  }

  describe("validating asset status") {
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
