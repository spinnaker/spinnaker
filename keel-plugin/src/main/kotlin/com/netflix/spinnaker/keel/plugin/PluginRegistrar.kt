package com.netflix.spinnaker.keel.plugin

import com.netflix.appinfo.InstanceInfo
import com.netflix.appinfo.InstanceInfo.InstanceStatus.UP
import com.netflix.discovery.EurekaClient
import com.netflix.spinnaker.keel.api.engine.AssetPluginRegistryGrpc
import com.netflix.spinnaker.keel.api.engine.AssetPluginRegistryGrpc.AssetPluginRegistryBlockingStub
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginRequest
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

@Component
class PluginRegistrar(
  private val eurekaClient: EurekaClient,
  private val plugins: List<AssetPlugin>,
  @Value("\${keel.registry.address:keel-test.us-west-2.spinnaker.netflix.net}") private val keelRegistryAddress: String,
  private val instanceInfo: InstanceInfo
) : ApplicationListener<RemoteStatusChangedEvent> {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun onApplicationEvent(event: RemoteStatusChangedEvent) {
    if (event.source.status == UP) {
      onDiscoveryUp()
    }
    // TODO: should deregister as well
  }

  fun onDiscoveryUp() {
    plugins.forEach { plugin ->
      plugin.registerWith(registry)
    }
  }

  private fun AssetPlugin.registerWith(registry: AssetPluginRegistryBlockingStub) {
    val request = RegisterAssetPluginRequest
      .newBuilder()
      .apply {
        vipAddress = instanceInfo.vipAddress
        addAllTypes(supportedTypes)
      }
      .build()
    registry.register(request).let { response ->
      if (response.succeeded) {
        log.info("Successfully registered {} with Keel", javaClass.simpleName)
      }
    }
  }

  private val registry: AssetPluginRegistryBlockingStub by lazy {
    eurekaClient.getNextServerFromEureka(keelRegistryAddress, false)
      ?.let(::createChannelTo)
      ?.let(AssetPluginRegistryGrpc::newBlockingStub)
      ?: throw IllegalStateException("Can't find keel in Eureka")
  }

  fun createChannelTo(it: InstanceInfo): ManagedChannel =
    ManagedChannelBuilder
      .forAddress(it.ipAddr, it.port)
      .usePlaintext()
      .build()
}
