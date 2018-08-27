package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetBase
import com.netflix.spinnaker.keel.model.AssetContainer
import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.model.PartialAsset
import com.netflix.spinnaker.keel.persistence.AssetState.Unknown
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import javax.annotation.PostConstruct

@Component
//@ConditionalOnMissingBean(AssetRepository::class)
class InMemoryAssetRepository(
  private val clock: Clock
) : AssetRepository {
  private val assets = mutableMapOf<AssetId, Asset>()
  private val partialAssets = mutableMapOf<AssetId, PartialAsset>()
  private val states = mutableMapOf<AssetId, Pair<AssetState, Instant>>()

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun rootAssets(callback: (Asset) -> Unit) {
    assets.values.filter { it.dependsOn.isEmpty() }.forEach(callback)
  }

  override fun get(id: AssetId): Asset? =
    assets[id]

  override fun getPartial(id: AssetId): PartialAsset? =
    partialAssets[id]

  override fun getContainer(id: AssetId): AssetContainer =
    AssetContainer(
      asset = get(id),
      partialAssets = partialAssets.filterValues { it.root.value == id.value }.values.toSet()
    )

  override fun store(asset: AssetBase) {
    when (asset) {
      is Asset -> assets[asset.id] = asset
      is PartialAsset -> partialAssets[asset.id] = asset
      else -> throw IllegalArgumentException("Unknown asset type: ${asset.javaClass.simpleName}")
    }
    states[asset.id] = Unknown to clock.instant()
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

  @PostConstruct
  fun onInitialize() {
    log.warn("Using in-memory asset registry")
  }
}

