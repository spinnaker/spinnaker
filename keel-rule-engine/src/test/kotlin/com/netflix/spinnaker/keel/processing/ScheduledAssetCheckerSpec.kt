package com.netflix.spinnaker.keel.processing

import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.persistence.InMemoryAssetRepository
import com.netflix.spinnaker.q.Queue
import com.nhaarman.mockito_kotlin.argThat
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
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
        id = AssetId("SecurityGroup:ec2:prod:us-west-2:keel"),
        kind = "SecurityGroup",
        spec = randomBytes()
      )
      val rootAsset2 = Asset(
        id = AssetId("SecurityGroup:ec2:prod:us-east-1:keel"),
        kind = "SecurityGroup",
        spec = randomBytes()
      )
      val assets = listOf(rootAsset1, rootAsset2).flatMap {
        listOf(
          Asset(
            id = AssetId(it.id.value.replace("SecurityGroup", "LoadBalancer")),
            kind = "LoadBalancer",
            dependsOn = setOf(it.id),
            spec = randomBytes()
          ),
          Asset(
            id = AssetId(it.id.value.replace("SecurityGroup", "Cluster")),
            kind = "Cluster",
            dependsOn = setOf(it.id),
            spec = randomBytes()
          )
        )
      }

      beforeGroup {
        (assets + rootAsset1 + rootAsset2).forEach(repository::store)
      }

      afterGroup { repository.dropAll() }

      on("running the check cycle") {
        subject.runCheckCycle()
      }

      it("requests validation for each root asset") {
        verify(queue).push(ValidateAssetTree(rootAsset1.id))
        verify(queue).push(ValidateAssetTree(rootAsset2.id))
      }

      it("does not request validation for any dependent assets") {
        verify(queue, never()).push(argThat<ValidateAssetTree> {
          rootId in assets.map(Asset::id)
        })
      }
    }
  }

})
