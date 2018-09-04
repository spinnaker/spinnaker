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
package com.netflix.spinnaker.keel.registry

import com.netflix.discovery.EurekaClient
import com.netflix.spinnaker.keel.api.TypeMetadata
import com.netflix.spinnaker.keel.api.VetoPluginGrpc
import com.netflix.spinnaker.keel.api.engine.PluginRegistryGrpc
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginRequest
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginResponse
import com.netflix.spinnaker.keel.api.engine.RegisterVetoPluginRequest
import com.netflix.spinnaker.keel.api.engine.RegisterVetoPluginResponse
import com.netflix.spinnaker.keel.api.plugin.AssetPluginGrpc
import com.netflix.spinnaker.keel.platform.NoSuchVip
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.AbstractStub
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GrpcPluginRegistry(
  private val eurekaClient: EurekaClient
) : PluginRegistryGrpc.PluginRegistryImplBase() {

  private val log = LoggerFactory.getLogger(javaClass)
  private val assetPlugins: MutableMap<TypeMetadata, String> = mutableMapOf()
  private val vetoPlugins: MutableSet<String> = mutableSetOf()

  fun pluginFor(type: TypeMetadata): AssetPluginGrpc.AssetPluginBlockingStub? =
    assetPlugins[type]?.let { name ->
      stubFor(name, AssetPluginGrpc::newBlockingStub)
    }

  fun <R> applyVetos(callback: (VetoPluginGrpc.VetoPluginBlockingStub) -> R): Iterable<R> =
    vetoPlugins
      .map { name -> stubFor(name, VetoPluginGrpc::newBlockingStub) }
      .map(callback)

  override fun registerAssetPlugin(
    request: RegisterAssetPluginRequest,
    responseObserver: StreamObserver<RegisterAssetPluginResponse>
  ) {
    request
      .typesList
      .forEach { type ->
        assetPlugins[type] = request.vipAddress
        log.info("Registered asset plugin at \"${request.vipAddress}\" supporting $type")
      }
    responseObserver.apply {
      onNext(registerAssetPluginSuccessResponse)
      onCompleted()
    }
  }

  override fun registerVetoPlugin(
    request: RegisterVetoPluginRequest,
    responseObserver: StreamObserver<RegisterVetoPluginResponse>
  ) {
    vetoPlugins.add(request.vipAddress)
    log.info("Registered veto plugin at \"${request.vipAddress}\"")
    responseObserver.apply {
      onNext(
        RegisterVetoPluginResponse.newBuilder()
          .apply { succeeded = true }
          .build()
      )
      onCompleted()
    }
  }

  fun <T : AbstractStub<T>> stubFor(name: String, stubFactory: (ManagedChannel) -> T): T =
    try {
      eurekaClient
        .getNextServerFromEureka(name, false)
        .let { address ->
          ManagedChannelBuilder
            .forAddress(address.ipAddr, address.port)
            .usePlaintext()
            .build()
            .let(stubFactory)
        }
    } catch (e: RuntimeException) {
      throw NoSuchVip(name, e)
    }
}

val registerAssetPluginSuccessResponse: RegisterAssetPluginResponse =
  RegisterAssetPluginResponse
    .newBuilder()
    .apply { succeeded = true }
    .build()
