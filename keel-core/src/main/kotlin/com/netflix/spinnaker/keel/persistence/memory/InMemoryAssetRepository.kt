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
package com.netflix.spinnaker.keel.persistence.memory

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.api.AssetName
import com.netflix.spinnaker.keel.persistence.AssetRepository
import com.netflix.spinnaker.keel.persistence.AssetState
import com.netflix.spinnaker.keel.persistence.AssetState.Unknown
import com.netflix.spinnaker.keel.persistence.NoSuchAssetException
import java.time.Clock
import java.time.Instant

class InMemoryAssetRepository(
  private val clock: Clock = Clock.systemDefaultZone()
) : AssetRepository {
  private val assets = mutableMapOf<AssetName, Asset<*>>()
  private val states = mutableMapOf<AssetName, Pair<AssetState, Instant>>()

  override fun allAssets(callback: (Triple<AssetName, ApiVersion, String>) -> Unit) {
    assets.values.forEach {
      callback(Triple(it.metadata.name, it.apiVersion, it.kind))
    }
  }

  private val mapper by lazy { jacksonObjectMapper() }

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> get(name: AssetName, specType: Class<T>): Asset<T> =
    assets[name]?.let {
      if (specType.isAssignableFrom(it.spec.javaClass)) {
        it as Asset<T>
      } else {
        val convertedSpec = mapper.convertValue(it.spec, specType)
        (it as Asset<Any>).copy(spec = convertedSpec) as Asset<T>
      }
    } ?: throw NoSuchAssetException(name)

  override fun store(asset: Asset<*>) {
    assets[asset.metadata.name] = asset
    states[asset.metadata.name] = Unknown to clock.instant()
  }

  override fun delete(name: AssetName) {
    assets.remove(name)
    states.remove(name)
  }

  override fun lastKnownState(name: AssetName): Pair<AssetState, Instant>? =
    states[name]

  override fun updateState(name: AssetName, state: AssetState) {
    states[name] = state to clock.instant()
  }

  fun dropAll() {
    assets.clear()
  }
}

