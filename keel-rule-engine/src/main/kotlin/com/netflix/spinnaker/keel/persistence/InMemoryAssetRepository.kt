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
package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetBase
import com.netflix.spinnaker.keel.model.AssetContainer
import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.model.PartialAsset
import com.netflix.spinnaker.keel.persistence.AssetState.Unknown
import java.time.Clock
import java.time.Instant

class InMemoryAssetRepository(
  private val clock: Clock
) : AssetRepository {
  private val assets = mutableMapOf<AssetId, Asset>()
  private val partialAssets = mutableMapOf<AssetId, PartialAsset>()
  private val states = mutableMapOf<AssetId, Pair<AssetState, Instant>>()

  override fun allAssets(callback: (AssetBase) -> Unit) {
    assets.values.forEach(callback)
    partialAssets.values.forEach(callback)
  }

  override fun rootAssets(callback: (Asset) -> Unit) {
    assets.values.filter { it.dependsOn.isEmpty() }.forEach(callback)
  }

  override fun get(id: AssetId): Asset? =
    assets[id]

  override fun getPartial(id: AssetId): PartialAsset? =
    partialAssets[id]

  override fun getContainer(id: AssetId): AssetContainer? =
    get(id)?.let {
      AssetContainer(
        asset = it,
        partialAssets = partialAssets.filterValues { it.root.value == id.value }.values.toSet()
      )
    }

  override fun store(asset: AssetBase) {
    when (asset) {
      is Asset -> assets[asset.id] = asset
      is PartialAsset -> partialAssets[asset.id] = asset
      else -> throw IllegalArgumentException("Unknown asset type: ${asset.javaClass.simpleName}")
    }
    states[asset.id] = Unknown to clock.instant()
  }

  override fun delete(id: AssetId) {
    assets.remove(id)
  }

  override fun dependents(id: AssetId): Iterable<AssetId> =
    assets
      .filter { it.value.dependsOn.contains(id) }
      .keys

  override fun lastKnownState(id: AssetId): Pair<AssetState, Instant>? =
    states[id]

  override fun updateState(id: AssetId, state: AssetState) {
    states[id] = state to clock.instant()
  }

  internal fun dropAll() {
    assets.clear()
  }
}

