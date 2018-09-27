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

import com.netflix.spinnaker.keel.api.plugin.ConvergeStatus.ACCEPTED
import com.netflix.spinnaker.keel.model.AssetContainer
import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.processing.AssetService
import com.netflix.spinnaker.keel.processing.CurrentAssetPair
import com.netflix.spinnaker.keel.registry.UnsupportedAssetType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import com.netflix.spinnaker.keel.api.Asset as AssetProto
import com.netflix.spinnaker.keel.api.AssetId as AssetIdProto

@Component
class GrpcAssetService(
  private val pluginRegistry: GrpcPluginRegistry
) : AssetService {
  // TODO: this would be ripe for a suspending function if not using gRPC blocking stub
  override fun current(assetContainer: AssetContainer): CurrentAssetPair {
    if (assetContainer.asset == null) {
      throw AssetRequired()
    }
    val typeMetaData = assetContainer.asset.toTypeMetaData()

    val stub = pluginRegistry
      .pluginFor(typeMetaData) ?: throw UnsupportedAssetType(typeMetaData)
    return stub.current(assetContainer.toProto()).let { response ->
      if (response.hasSuccess()) {
        with(response.success) {
          if (!hasDesired()) {
            throw PluginMissingDesiredState()
          }
          if (hasCurrent()) {
            CurrentAssetPair(desired.fromProto(), current.fromProto())
          } else {
            CurrentAssetPair(desired.fromProto(), null)
          }
        }
      } else {
        throw CurrentFailed(assetContainer.asset.id, response.failure.reason)
      }
    }
  }

  override fun converge(assetContainer: AssetContainer) {
    if (assetContainer.asset == null) {
      throw AssetRequired()
    }
    val typeMetaData = assetContainer.asset.toTypeMetaData()

    val stub = pluginRegistry
      .pluginFor(typeMetaData) ?: throw UnsupportedAssetType(typeMetaData)
    stub.converge(assetContainer.toProto()).let { response ->
      when (response.status) {
        ACCEPTED ->
          log.info("Request to converge {} accepted", assetContainer.asset.id)
        else -> {
          log.error("Request to converge {} failed", assetContainer.asset.id)
          throw ConvergeFailed(assetContainer.asset.id, response.failure.reason)
        }
      }
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private class AssetRequired : IllegalArgumentException("An asset must be provided to get its current state")

  private class PluginMissingDesiredState : RuntimeException("Plugin did not respond with desired asset object")

  private class CurrentFailed(id: AssetId, reason: String) : PluginRequestFailed(id, "current", reason)

  private class ConvergeFailed(id: AssetId, reason: String) : PluginRequestFailed(id, "converge", reason)
}
