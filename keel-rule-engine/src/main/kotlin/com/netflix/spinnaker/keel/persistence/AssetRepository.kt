package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetId
import java.time.Instant

interface AssetRepository {
  /**
   * Invokes [callback] once with each root asset (an asset with no
   * dependencies).
   */
  fun rootAssets(callback: (Asset) -> Unit)

  /**
   * Retrieves a single asset by id.
   *
   * @return The asset represented by [id] or `null` if [id] is unknown.
   */
  fun get(id: AssetId): Asset?

  /**
   * Persists an asset.
   */
  fun store(asset: Asset)

  /**
   * Fetches the ids of any assets that depend (directly) on [id].
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
}
