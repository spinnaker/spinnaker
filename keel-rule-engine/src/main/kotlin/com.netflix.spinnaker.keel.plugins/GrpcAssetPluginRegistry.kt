package com.netflix.spinnaker.keel.plugins

import com.netflix.discovery.EurekaClient
import com.netflix.spinnaker.keel.api.TypeMetadata
import com.netflix.spinnaker.keel.api.engine.AssetPluginRegistryGrpc
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginRequest
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginResponse
import com.netflix.spinnaker.keel.api.plugin.AssetPluginGrpc
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory

class GrpcAssetPluginRegistry(
  override val eurekaClient: EurekaClient
) : AssetPluginRegistryGrpc.AssetPluginRegistryImplBase(), Registry<AssetPluginGrpc.AssetPluginBlockingStub>, AssetPluginRegistry {

  private val log = LoggerFactory.getLogger(javaClass)
  private val assetPlugins: MutableMap<TypeMetadata, String> = mutableMapOf()
  override val stubFactory = AssetPluginGrpc::newBlockingStub

  override fun pluginFor(type: TypeMetadata): AssetPluginGrpc.AssetPluginBlockingStub? =
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
      onNext(registerAssetPluginSuccessResponse)
      onCompleted()
    }
  }
}

val registerAssetPluginSuccessResponse: RegisterAssetPluginResponse =
  RegisterAssetPluginResponse
    .newBuilder()
    .apply { succeeded = true }
    .build()
