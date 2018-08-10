package com.netflix.spinnaker.keel.plugins

import com.netflix.discovery.EurekaClient
import com.netflix.spinnaker.keel.api.TypeMetadata
import com.netflix.spinnaker.keel.api.engine.AssetPluginRegistryGrpc.AssetPluginRegistryImplBase
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginRequest
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginResponse
import com.netflix.spinnaker.keel.api.plugin.AssetPluginGrpc
import com.netflix.spinnaker.keel.api.plugin.AssetPluginGrpc.AssetPluginBlockingStub
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory

class AssetPluginRegistry(
  override val eurekaClient: EurekaClient
) : AssetPluginRegistryImplBase(), Registry<AssetPluginBlockingStub> {

  private val log = LoggerFactory.getLogger(javaClass)
  private val assetPlugins: MutableMap<TypeMetadata, String> = mutableMapOf()
  override val stubFactory = AssetPluginGrpc::newBlockingStub

  fun pluginFor(type: TypeMetadata): AssetPluginBlockingStub? =
    assetPlugins[type]?.let { name ->
      stubFor(name)
    }

  override fun register(
    request: RegisterAssetPluginRequest,
    responseObserver: StreamObserver<RegisterAssetPluginResponse>
  ) {
    request
      .typesList
      .forEach { type ->
        assetPlugins[type] = request.name
        log.info("Registered asset plugin \"${request.name}\" supporting $type")
      }
    responseObserver.apply {
      onNext(
        RegisterAssetPluginResponse
          .newBuilder()
          .apply { succeeded = true }
          .build()
      )
      onCompleted()
    }
  }
}

