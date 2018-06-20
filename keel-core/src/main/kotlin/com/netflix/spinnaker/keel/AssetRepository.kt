/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel

interface AssetRepository {

  fun upsertAsset(asset: Asset<AssetSpec>): Asset<AssetSpec>

  fun getAssets(): List<Asset<AssetSpec>>

  fun getAssets(statuses: List<AssetStatus>): List<Asset<AssetSpec>>

  fun getAsset(id: String): Asset<AssetSpec>?

  /**
   * Deletes an asset. If [preserveHistory] is true, the Asset will be updated
   * to INACTIVE. If false, the record will be physically removed from the
   * persistence store.
   *
   * Does not perform any action against the underlying resource.
   */
  fun deleteAsset(id: String, preserveHistory: Boolean = true)

  fun findByLabels(labels: Map<String, String>): List<Asset<AssetSpec>> =
    getAssets().filter { asset ->
      labels.all { asset.labels[it.key] == it.value  }
    }
}
