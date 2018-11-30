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
import com.oneeyedmen.minutest.junit.junitTests
import org.junit.jupiter.api.TestFactory
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.second
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

internal object ValidateAssetTreeHandlerTests {

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

  @TestFactory
  fun `validating an asset`() = junitTests<Unit> {
    context("no desired state is known") {
      after {
        repository.dropAll()
        reset(queue)
      }

      test("on receiving a message it does not try to upsert any assets") {
        subject.handle(message)

        verifyZeroInteractions(queue)
      }
    }

    context("assets exist in the repository") {
      before {
        repository.store(rootAsset)
      }

      after {
        repository.dropAll()
      }

      context("all states match the desired state") {
        before {
          whenever(assetService.current(rootAsset)) doReturn CurrentAssetPair(rootAsset, rootAsset)
        }

        after { reset(assetService, queue) }

        test("on receiving a message it does not try to upsert any assets") {
          subject.handle(message)

          verifyZeroInteractions(queue)
        }

        test("it marks the asset as $Ok") {
          subject.handle(message)

          repository.lastKnownState(rootAsset.id) expect {
            isNotNull().and {
              first.isEqualTo(Ok)
              second.isEqualTo(clock.instant())
            }
          }
        }
      }

      context("the current state of the asset differs from the desired state") {
        before {
          whenever(assetService.current(rootAsset)) doReturn CurrentAssetPair(rootAsset, rootAsset.copy(spec = randomData()))
        }

        after { reset(assetService, queue) }

        test("it requests convergence of the invalid asset") {
          subject.handle(message)

          verify(queue).push(ConvergeAsset(rootAsset.id))
        }

        test("it marks the asset as $Diff") {
          subject.handle(message)

          repository.lastKnownState(rootAsset.id) expect {
            isNotNull().and {
              first.isEqualTo(Diff)
              second.isEqualTo(clock.instant())
            }
          }
        }
      }

      context("the asset does not exist in the cloud") {
        before {
          whenever(assetService.current(rootAsset)) doReturn CurrentAssetPair(rootAsset, null)
        }

        after { reset(assetService, queue) }

        test("it requests convergence of all missing assets") {
          subject.handle(message)

          verify(queue).push(ConvergeAsset(rootAsset.id))
        }

        test("it marks missing assets as $Missing") {
          subject.handle(message)

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
}

infix fun <T> T.expect(block: Assertion.Builder<T>.() -> Unit) =
  expectThat(this, block)
