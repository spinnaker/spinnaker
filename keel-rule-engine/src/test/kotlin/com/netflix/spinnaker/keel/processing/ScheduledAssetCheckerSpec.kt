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
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.time.Clock

internal object ScheduledAssetCheckerSpec : Spek({

  val clock = Clock.systemDefaultZone()
  val repository = InMemoryAssetRepository(clock)
  val queue: Queue = mock()
  val subject = ScheduledAssetChecker(repository, queue)

  describe("checking assets") {
    given("the repository contains some assets") {

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

      beforeGroup {
        assets.forEach(repository::store)
      }

      afterGroup { repository.dropAll() }

      on("running the check cycle") {
        subject.runCheckCycle()
      }

      it("requests validation for each asset") {
        verify(queue).push(ValidateAsset(rootAsset1.id))
        verify(queue).push(ValidateAsset(rootAsset2.id))
      }
    }
  }

})
