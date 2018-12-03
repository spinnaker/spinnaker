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

import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.api.AssetName
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.persistence.AssetRepository
import com.netflix.spinnaker.keel.persistence.AssetState
import com.netflix.spinnaker.keel.persistence.AssetState.Unknown
import java.time.Clock
import java.time.Instant

class InMemoryAssetRepository(
  private val clock: Clock
) : AssetRepository {
  private val assets = mutableMapOf<AssetName, Asset<*>>()
  private val states = mutableMapOf<AssetName, Pair<AssetState, Instant>>()

  override fun allAssets(callback: (Asset<*>) -> Unit) {
    assets.values.forEach(callback)
  }

  override fun get(name: AssetName): Asset<*>? =
    assets[name]

  override fun store(asset: Asset<*>) {
    assets[asset.id] = asset
    states[asset.id] = Unknown to clock.instant()
  }

  override fun delete(name: AssetName) {
    assets.remove(name)
  }

  override fun lastKnownState(name: AssetName): Pair<AssetState, Instant>? =
    states[name]

  override fun updateState(name: AssetName, state: AssetState) {
    states[name] = state to clock.instant()
  }

  internal fun dropAll() {
    assets.clear()
  }
}

