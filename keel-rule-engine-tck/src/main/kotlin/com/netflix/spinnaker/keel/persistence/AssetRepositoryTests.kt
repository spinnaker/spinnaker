package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetBase
import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.model.PartialAsset
import com.netflix.spinnaker.keel.model.TypedByteArray
import com.netflix.spinnaker.keel.persistence.AssetState.Diff
import com.netflix.spinnaker.keel.persistence.AssetState.Ok
import com.netflix.spinnaker.keel.persistence.AssetState.Unknown
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.oneeyedmen.minutest.junit.junitTests
import org.junit.jupiter.api.TestFactory
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.*

abstract class AssetRepositoryTests<T : AssetRepository> {

  abstract fun factory(clock: Clock): T

  open fun flush() {}

  data class Fixture<T : AssetRepository>(
    val subject: T,
    val callback: (AssetBase) -> Unit
  )

  @TestFactory
  fun `an asset repository`() = junitTests<Fixture<T>>() {

    fixture {
      val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
      Fixture(
        subject = factory(clock),
        callback = mock()
      )
    }

    after { reset(callback) }
    after { flush() }

    context("no assets exist") {
      test("rootAssets is a no-op") {
        subject.rootAssets(callback)

        verifyZeroInteractions(callback)
      }

      test("allAssets is a no-op") {
        subject.allAssets(callback)

        verifyZeroInteractions(callback)
      }
    }

    context("an asset with no dependencies") {
      val asset = Asset(
        id = AssetId("SecurityGroup:ec2:test:us-west-2:fnord"),
        apiVersion = "1.0",
        kind = "ec2:SecurityGroup",
        spec = randomBytes()
      )

      before {
        subject.store(asset)
      }

      test("it is returned by rootAssets") {
        subject.rootAssets(callback)

        verify(callback).invoke(asset)
      }

      test("it is returned by allAssets") {
        subject.allAssets(callback)

        verify(callback).invoke(asset)
      }

      test("it can be retrieved by id") {
        expectThat(subject.get(asset.id)).isEqualTo(asset)
      }

      test("it can be retrieved in a container with no partials") {
        expectThat(subject.getContainer(asset.id))
          .isNotNull()
          .and {
            get { this.asset }.isEqualTo(asset)
            get { partialAssets }.isEmpty()
          }
      }

      test("its state is unknown") {
        expectThat(subject.lastKnownState(asset.id))
          .isNotNull()
          .first
          .isEqualTo(Unknown)
      }

      context("storing another asset with a different id") {
        val anotherAsset = Asset(
          id = AssetId("SecurityGroup:ec2:test:us-east-1:fnord"),
          apiVersion = "1.0",
          kind = "ec2:SecurityGroup",
          spec = randomBytes()
        )

        before {
          subject.store(anotherAsset)
        }

        test("it does not overwrite the first asset") {
          subject.rootAssets(callback)

          argumentCaptor<Asset>().apply {
            verify(callback, times(2)).invoke(capture())
            expectThat(allValues)
              .hasSize(2)
              .containsExactlyInAnyOrder(asset, anotherAsset)
          }
        }
      }


      context("storing a new version of the asset") {
        val updatedAsset = asset.copy(
          spec = randomBytes()
        )

        before {
          subject.store(updatedAsset)
        }

        test("it replaces the original asset") {
          expectThat(subject.get(asset.id))
            .isNotNull()
            .get(Asset::spec)
            .isEqualTo(updatedAsset.spec)
        }
      }

      context("updating the state of the asset") {
        before {
          subject.updateState(asset.id, Ok)
        }

        test("it reports the new state") {
          expectThat(subject.lastKnownState(asset.id))
            .isNotNull()
            .first
            .isEqualTo(Ok)
        }

        context("updating the state again") {
          before {
            subject.updateState(asset.id, Diff)
          }

          test("it reports the newest state") {
            expectThat(subject.lastKnownState(asset.id))
              .isNotNull()
              .first
              .isEqualTo(Diff)
          }
        }
      }

      context("deleting the asset") {
        before {
          subject.delete(asset.id)
        }

        test("the asset is no longer returned by all assets") {
          subject.allAssets(callback)

          verifyZeroInteractions(callback)
        }

        test("the asset can no longer be retrieved by id") {
          expectThat(subject.get(asset.id)).isNull()
        }
      }
    }

    context("an asset with dependencies") {
      val rootAsset = Asset(
        id = AssetId("SecurityGroup:ec2:test:us-east-1:fnord"),
        apiVersion = "1.0",
        kind = "ec2:SecurityGroup",
        spec = randomBytes()
      )
      val dependentAsset = Asset(
        id = AssetId("LoadBalancer:ec2:test:us-west-2:fnord"),
        apiVersion = "1.0",
        kind = "ec2:LoadBalancer",
        dependsOn = setOf(rootAsset.id),
        spec = randomBytes()
      )

      before {
        subject.store(rootAsset)
        subject.store(dependentAsset)
      }

      test("it is not returned by rootAssets") {
        subject.rootAssets(callback)

        verify(callback, never()).invoke(dependentAsset)
      }

      test("it is returned by allAssets") {
        subject.allAssets(callback)

        verify(callback).invoke(dependentAsset)
      }

      test("it can be retrieved in a container with no partials") {
        expectThat(subject.getContainer(dependentAsset.id))
          .isNotNull()
          .and {
            get { this.asset }.isEqualTo(dependentAsset)
            get { partialAssets }.isEmpty()
          }
      }

      test("it can be retrieved by the id of the asset it depends on") {
        expectThat(subject.dependents(rootAsset.id)).containsExactly(dependentAsset.id)
      }
    }

    context("a partial asset") {
      val asset = Asset(
        id = AssetId("SecurityGroup:ec2:test:us-west-2:fnord"),
        apiVersion = "1.0",
        kind = "ec2:SecurityGroup",
        spec = randomBytes()
      )

      val partial = PartialAsset(
        id = AssetId("SecurityGroupRule:ec2:test:us-west-2:fnord:whatever"),
        root = asset.id,
        apiVersion = "1.0",
        kind = "ec2:SecurityGroupRule",
        spec = randomBytes()
      )

      before {
        subject.store(asset)
        subject.store(partial)
      }

      test("it is returned by allAssets") {
        subject.allAssets(callback)

        verify(callback).invoke(asset)
        verify(callback).invoke(partial)
      }

      test("it is not returned by rootAssets") {
        subject.rootAssets(callback)

        verify(callback).invoke(asset)
        verify(callback, never()).invoke(partial)
      }

      test("it can be retrieved by id") {
        expectThat(subject.getPartial(partial.id)).isEqualTo(partial)
      }

      test("it can be retrieved alongside its parent asset") {
        expectThat(subject.getContainer(asset.id))
          .isNotNull()
          .and {
            get { this.asset }.isEqualTo(asset)
            get { partialAssets }.hasSize(1).first().isEqualTo(partial)
          }
      }
    }
  }
}

fun randomBytes(length: Int = 20) =
  TypedByteArray(
    "whatever",
    ByteArray(length).also(Random(System.nanoTime())::nextBytes)
  )
