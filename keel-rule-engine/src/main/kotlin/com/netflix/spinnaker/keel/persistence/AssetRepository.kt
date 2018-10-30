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
import java.time.Instant

interface AssetRepository {
  /**
   * Invokes [callback] once with each root asset (an asset with no
   * dependencies).
   */
  fun rootAssets(callback: (Asset) -> Unit)

  /**
   * Invokes [callback] once with each registered asset.
   */
  fun allAssets(callback: (AssetBase) -> Unit)

  /**
   * Retrieves a single asset by id.
   *
   * @return The asset represented by [id] or `null` if [id] is unknown.
   */
  fun get(id: AssetId): Asset?

  /**
   * Fetches the ids of any assets that depend (directly) on [id].
   * Get a partial asset
   */
  fun getPartial(id: AssetId): PartialAsset?

  /**
   * Get an asset including all associated partial assets
   */
  fun getContainer(id: AssetId): AssetContainer?

  /**
   * Persists an asset or partial asset
   */
  fun store(asset: AssetBase)

  /**
   * Get the dependents of an asset id
   */
  fun dependents(id: AssetId): Iterable<AssetId>

  /**
   * Retrieves the last known state of an asset.
   *
   * @return The last known state of the asset represented by [id] or `null` if
   * [id] is unknown.
   */
  fun lastKnownState(id: AssetId): Pair<AssetState, Instant>?

  /**
   * Updates the last known state of the asset represented by [id].
   */
  fun updateState(id: AssetId, state: AssetState)

  /**
   * Deletes the asset represented by [id].
   */
  fun delete(id: AssetId)
}
