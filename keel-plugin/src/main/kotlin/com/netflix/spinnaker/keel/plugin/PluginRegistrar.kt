package com.netflix.spinnaker.keel.plugin

import com.netflix.discovery.EurekaClient
import com.netflix.spinnaker.keel.api.engine.AssetPluginRegistryGrpc
import com.netflix.spinnaker.keel.api.engine.AssetPluginRegistryGrpc.AssetPluginRegistryBlockingStub
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginRequest
import io.grpc.ManagedChannelBuilder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
class PluginRegistrar(
  private val eurekaClient: EurekaClient,
  private val plugins: List<AssetPlugin>
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @PostConstruct
  fun registerPlugins() {
    registry().let { registry ->
      plugins.forEach { plugin ->
        plugin.register(registry)
      }
    }
  }

  private fun AssetPlugin.register(registry: AssetPluginRegistryBlockingStub) {
    registry.register(
      RegisterAssetPluginRequest
        .newBuilder()
        .addAllTypes(supportedTypes)
        .build()
    ).let { response ->
      if (response.succeeded) {
        log.info("Successfully registered {} with Keel", javaClass.simpleName)
      }
    }
  }

  private fun registry(): AssetPluginRegistryBlockingStub {
    return eurekaClient
      .getNextServerFromEureka("keel", false)
      ?.let { address ->
        ManagedChannelBuilder
          .forAddress(address.ipAddr, address.port)
          .usePlaintext()
          .build()
          .let(AssetPluginRegistryGrpc::newBlockingStub)
      } ?: throw IllegalStateException("Can't find keel in Eureka")
  }
}
