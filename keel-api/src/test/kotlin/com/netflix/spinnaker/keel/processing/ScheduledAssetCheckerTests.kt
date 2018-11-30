package com.netflix.spinnaker.keel.processing

import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.api.AssetMetadata
import com.netflix.spinnaker.keel.api.AssetName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.persistence.InMemoryAssetRepository
import com.netflix.spinnaker.keel.persistence.randomData
import com.netflix.spinnaker.q.Queue
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.oneeyedmen.minutest.junit.junitTests
import org.junit.jupiter.api.TestFactory
import java.time.Clock

internal object ScheduledAssetCheckerTests {

  val clock = Clock.systemDefaultZone()
  val repository = InMemoryAssetRepository(clock)
  val queue: Queue = mock()
  val subject = ScheduledAssetChecker(repository, queue)

  @TestFactory
  fun `checking assets`() = junitTests<Unit> {
    context("the repository contains some assets") {

      val rootAsset1 = Asset(
        apiVersion = SPINNAKER_API_V1,
        metadata = AssetMetadata(
          name = AssetName("SecurityGroup:ec2:prod:us-west-2:keel")
        ),
        kind = "ec2.SecurityGroup",
        spec = randomData()
      )
      val rootAsset2 = Asset(
        apiVersion = SPINNAKER_API_V1,
        metadata = AssetMetadata(
          name = AssetName("SecurityGroup:ec2:prod:us-east-1:keel")
        ),
        kind = "ec2.SecurityGroup",
        spec = randomData()
      )
      val assets = listOf(rootAsset1, rootAsset2)

      before {
        assets.forEach(repository::store)
      }

      after { repository.dropAll() }

      test("on running the check cycle it requests validation for each asset") {
        subject.runCheckCycle()

        verify(queue).push(ValidateAsset(rootAsset1.id))
        verify(queue).push(ValidateAsset(rootAsset2.id))
      }
    }
  }
}
