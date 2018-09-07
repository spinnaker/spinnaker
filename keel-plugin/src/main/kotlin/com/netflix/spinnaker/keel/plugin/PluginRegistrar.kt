/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.plugin

import com.netflix.appinfo.InstanceInfo
import com.netflix.appinfo.InstanceInfo.InstanceStatus.UP
import com.netflix.discovery.EurekaClient
import com.netflix.spinnaker.keel.api.engine.PluginRegistryGrpc
import com.netflix.spinnaker.keel.api.engine.PluginRegistryGrpc.PluginRegistryBlockingStub
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginRequest
import com.netflix.spinnaker.keel.api.engine.RegisterVetoPluginRequest
import com.netflix.spinnaker.keel.platform.NoSuchVip
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.lognet.springboot.grpc.context.LocalRunningGrpcPort
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

@Component
class PluginRegistrar(
  private val eurekaClient: EurekaClient,
  private val plugins: List<KeelPlugin>,
  private val properties: PluginProperties,
  @LocalRunningGrpcPort private val localGrpcPort: Int,
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
      plugin.registerWith(pluginRegistry)
    }
  }

  private fun KeelPlugin.registerWith(registry: PluginRegistryBlockingStub) {
    log.info("Registering {} with Keel at {} port {}", javaClass.simpleName, properties.registryVip, properties.registryPort)
    when (this) {
      is AssetPlugin -> {
        val request = RegisterAssetPluginRequest
          .newBuilder()
          .also {
            it.name = name
            it.vip = instanceInfo.vipAddress
            it.port = localGrpcPort
            it.addAllTypes(supportedTypes)
          }
          .build()
        registry.registerAssetPlugin(request).let { response ->
          if (response.succeeded) {
            log.info("Successfully registered {} with Keel", javaClass.simpleName)
          }
        }
      }
      is VetoPlugin -> {
        val request = RegisterVetoPluginRequest
          .newBuilder()
          .also {
            it.name = name
            it.vip = instanceInfo.vipAddress
            it.port = localGrpcPort
          }
          .build()
        registry.registerVetoPlugin(request).let { response ->
          if (response.succeeded) {
            log.info("Successfully registered {} with Keel", javaClass.simpleName)
          }
        }
      }
    }
  }

  private val pluginRegistry: PluginRegistryBlockingStub by lazy {
    try {
      eurekaClient.getNextServerFromEureka(properties.registryVip, false)
        .let { createChannelTo(it, properties.registryPort) }
        .let(PluginRegistryGrpc::newBlockingStub)
    } catch (e: RuntimeException) {
      throw NoSuchVip(properties.registryVip, e)
      // TODO: need to fail health check in this case
    }
  }

  fun createChannelTo(instance: InstanceInfo, port: Int = instance.port): ManagedChannel =
    ManagedChannelBuilder
      .forAddress(instance.ipAddr, port)
      .usePlaintext()
      .build()
}
