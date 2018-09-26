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
package com.netflix.spinnaker.keel.grpc

import com.netflix.discovery.EurekaClient
import com.netflix.spinnaker.keel.api.TypeMetadata
import com.netflix.spinnaker.keel.api.VetoPluginGrpc
import com.netflix.spinnaker.keel.api.VetoPluginGrpc.VetoPluginBlockingStub
import com.netflix.spinnaker.keel.api.engine.PluginRegistryGrpc
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginRequest
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginResponse
import com.netflix.spinnaker.keel.api.engine.RegisterVetoPluginRequest
import com.netflix.spinnaker.keel.api.engine.RegisterVetoPluginResponse
import com.netflix.spinnaker.keel.api.plugin.AssetPluginGrpc
import com.netflix.spinnaker.keel.api.plugin.AssetPluginGrpc.AssetPluginBlockingStub
import com.netflix.spinnaker.keel.platform.NoSuchVip
import com.netflix.spinnaker.keel.registry.AssetType
import com.netflix.spinnaker.keel.registry.PluginRepository
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.AbstractStub
import io.grpc.stub.StreamObserver
import org.lognet.springboot.grpc.GRpcService
import org.slf4j.LoggerFactory

@GRpcService
class GrpcPluginRegistry(
  private val eurekaClient: EurekaClient,
  private val pluginRepository: PluginRepository
) : PluginRegistryGrpc.PluginRegistryImplBase() {

  private val log = LoggerFactory.getLogger(javaClass)

  fun pluginFor(type: TypeMetadata): AssetPluginBlockingStub? =
    pluginRepository
      .assetPluginFor(AssetType(type.kind, type.apiVersion))
      ?.let { (_, vip, port) ->
        stubFor(vip, port, AssetPluginGrpc::newBlockingStub)
      }

  fun <R> applyVetos(callback: (VetoPluginBlockingStub) -> R): Iterable<R> =
    pluginRepository
      .vetoPlugins()
      .map { (_, vip, port) -> stubFor(vip, port, VetoPluginGrpc::newBlockingStub) }
      .map(callback)

  override fun registerAssetPlugin(
    request: RegisterAssetPluginRequest,
    responseObserver: StreamObserver<RegisterAssetPluginResponse>
  ) {
    request
      .typeList
      .forEach { type ->
        pluginRepository.addAssetPluginFor(
          type.toAssetType(),
          request.toPluginAddress()
        ).also { added ->
          if (added) {
            log.info("Registered asset plugin supporting {} at vip: {} port: {}", type, request.vip, request.port)
          }
        }
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
    pluginRepository.addVetoPlugin(request.toPluginAddress()).also { added ->
      if (added) {
        log.info("Registered veto plugin at vip: {} port: {}", request.vip, request.port)
      }
    }
    responseObserver.apply {
      onNext(
        RegisterVetoPluginResponse.newBuilder()
          .apply { succeeded = true }
          .build()
      )
      onCompleted()
    }
  }

  fun <T : AbstractStub<T>> stubFor(vip: String, port: Int, stubFactory: (ManagedChannel) -> T): T =
    try {
      eurekaClient
        .getNextServerFromEureka(vip, false)
        .let { address ->
          ManagedChannelBuilder.forAddress(address.ipAddr, port)
            .usePlaintext()
            .build()
            .let(stubFactory)
        }
    } catch (e: RuntimeException) {
      throw NoSuchVip(vip, e)
    }
}


val registerAssetPluginSuccessResponse: RegisterAssetPluginResponse =
  RegisterAssetPluginResponse
    .newBuilder()
    .apply { succeeded = true }
    .build()
