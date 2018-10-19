package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetBase
import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.model.PartialAsset
import com.netflix.spinnaker.keel.model.TypedByteArray
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
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import java.util.*

abstract class AssetRepositoryTests<T : AssetRepository> {

  abstract fun factory(): T
  open fun flush() {}

  data class Fixture<T : AssetRepository>(
    val subject: T,
    val callback: (AssetBase) -> Unit
  )

  @TestFactory
  fun `an asset repository`() = junitTests<Fixture<T>>() {

    fixture {
      Fixture(
        subject = factory(),
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
    }

    context("an asset with dependencies") {
      val asset = Asset(
        id = AssetId("LoadBalancer:ec2:test:us-west-2:fnord"),
        apiVersion = "1.0",
        kind = "ec2:LoadBalancer",
        dependsOn = setOf(AssetId("SecurityGroup:ec2:test:us-west-2:fnord")),
        spec = randomBytes()
      )

      before {
        subject.store(asset)
      }

      test("it is not returned by rootAssets") {
        subject.rootAssets(callback)

        verify(callback, never()).invoke(asset)
      }

      test("it is returned by allAssets") {
        subject.allAssets(callback)

        verify(callback).invoke(asset)
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
    }
  }
}

fun randomBytes(length: Int = 20) =
  TypedByteArray(
    "whatever",
    ByteArray(length).also(Random(System.nanoTime())::nextBytes)
  )
