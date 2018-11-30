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

import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.api.AssetName
import java.time.Instant

interface AssetRepository {
  /**
   * Invokes [callback] once with each registered asset.
   */
  fun allAssets(callback: (Asset<*>) -> Unit)

  /**
   * Retrieves a single asset by its unique [com.netflix.spinnaker.keel.api.AssetMetadata.name].
   *
   * @return The asset represented by [name] or `null` if [name] is unknown.
   */
  fun get(name: AssetName): Asset<*>?

  /**
   * Persists an asset.
   *
   * @return the `uid` of the stored asset.
   */
  fun store(asset: Asset<*>)

  /**
   * Retrieves the last known state of an asset.
   *
   * @return The last known state of the asset represented by [name] or `null` if
   * [name] is unknown.
   */
  fun lastKnownState(name: AssetName): Pair<AssetState, Instant>?

  /**
   * Updates the last known state of the asset represented by [name].
   */
  fun updateState(name: AssetName, state: AssetState)

  /**
   * Deletes the asset represented by [name].
   */
  fun delete(name: AssetName)
}
