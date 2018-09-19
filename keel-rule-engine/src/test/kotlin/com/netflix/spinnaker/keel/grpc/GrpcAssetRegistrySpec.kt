package com.netflix.spinnaker.keel.grpc

import com.netflix.spinnaker.keel.api.AssetRegistryGrpc
import com.netflix.spinnaker.keel.api.GrpcStubManager
import com.netflix.spinnaker.keel.api.UpsertAssetRequest
import com.netflix.spinnaker.keel.api.UpsertAssetStatus.INSERTED
import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.model.PartialAsset
import com.netflix.spinnaker.keel.persistence.AssetRepository
import com.netflix.spinnaker.keel.processing.randomBytes
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

internal object GrpcAssetRegistrySpec : Spek({

  val assetRepository: AssetRepository = mock()
  val subject = GrpcAssetRegistry(assetRepository)

  val grpc = GrpcStubManager(AssetRegistryGrpc::newBlockingStub)

  beforeGroup {
    grpc.startServer {
      addService(subject)
    }
  }

  afterGroup {
    grpc.stopServer()
  }

  describe("upserting an asset") {
    given("no previous version of the asset exists") {
      given("a simple asset") {
        val asset = Asset(
          id = AssetId("aws:securityGroup:keel:keel-frontend:mgmt:us-west-2"),
          kind = "SecurityGroup",
          spec = randomBytes()
        )
        val request = UpsertAssetRequest.newBuilder()
          .apply {
            assetBuilder.asset = asset.toProto()
          }
          .build()

        afterGroup {
          reset(assetRepository)
        }

        on("upserting the asset") {
          grpc.withChannel { stub ->
            stub.upsertAsset(request)
              .also { response ->
                expectThat(response.resultList)
                  .hasSize(1)
                  .first()
                  .and {
                    map { it.status }.isEqualTo(INSERTED)
                  }
                  .and {
                    map { it.id.value }.isEqualTo(asset.id.value)
                  }
              }
          }
        }

        it("stores the asset") {
          verify(assetRepository).store(asset)
        }
      }

      given("an asset with associated partials") {
        val asset = Asset(
          id = AssetId("aws:securityGroup:keel:keel-frontend:mgmt:us-west-2"),
          kind = "SecurityGroup",
          spec = randomBytes()
        )
        val partial1 = PartialAsset(
          id = AssetId("aws:securityGroup:keel:keel-frontend:mgmt:us-west-2:ingress1"),
          root = asset.id,
          kind = "SecurityGroupRule",
          spec = randomBytes()
        )
        val partial2 = PartialAsset(
          id = AssetId("aws:securityGroup:keel:keel-frontend:mgmt:us-west-2:ingress2"),
          root = asset.id,
          kind = "SecurityGroupRule",
          spec = randomBytes()
        )
        val request = UpsertAssetRequest.newBuilder()
          .apply {
            assetBuilder.asset = asset.toProto()
            assetBuilder.addPartialAsset(partial1.toProto())
            assetBuilder.addPartialAsset(partial2.toProto())
          }
          .build()

        afterGroup {
          reset(assetRepository)
        }

        on("upserting the asset") {
          grpc.withChannel { stub ->
            stub.upsertAsset(request)
              .also { response ->
                expectThat(response.resultList)
                  .hasSize(3)
                  .and {
                    map {
                      it.map { it.status }
                    }.all { isEqualTo(INSERTED) }
                  }
                  .and {
                    map {
                      it.map { it.id.value }
                    }.containsExactlyInAnyOrder(asset.id.value, partial1.id.value, partial2.id.value)
                  }
              }
          }
        }

        it("stores the asset and its partials") {
          verify(assetRepository).store(asset)
          verify(assetRepository).store(partial1)
          verify(assetRepository).store(partial2)
        }
      }
    }
  }
})
