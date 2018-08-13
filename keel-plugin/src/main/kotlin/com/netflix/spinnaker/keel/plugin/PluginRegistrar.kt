package com.netflix.spinnaker.keel.plugin

import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.EurekaClient
import com.netflix.spinnaker.keel.api.engine.AssetPluginRegistryGrpc
import com.netflix.spinnaker.keel.api.engine.AssetPluginRegistryGrpc.AssetPluginRegistryBlockingStub
import io.grpc.ManagedChannel
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
    registry.register(registrationRequest).let { response ->
      if (response.succeeded) {
        log.info("Successfully registered {} with Keel", javaClass.simpleName)
      }
    }
  }

  private fun registry(): AssetPluginRegistryBlockingStub =
    eurekaClient
      .getNextServerFromEureka("keel", false)
      ?.let(::createChannelTo)
      ?.let(AssetPluginRegistryGrpc::newBlockingStub)
      ?: throw IllegalStateException("Can't find keel in Eureka")

  fun createChannelTo(instance: InstanceInfo): ManagedChannel =
    ManagedChannelBuilder
      .forAddress(instance.ipAddr, instance.port)
      .usePlaintext()
      .build()
}
