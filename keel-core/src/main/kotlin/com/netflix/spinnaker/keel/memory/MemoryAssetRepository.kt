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
package com.netflix.spinnaker.keel.memory

import com.netflix.spinnaker.keel.Asset
import com.netflix.spinnaker.keel.AssetRepository
import com.netflix.spinnaker.keel.AssetSpec
import com.netflix.spinnaker.keel.AssetStatus
import com.netflix.spinnaker.keel.event.AfterAssetUpsertEvent
import com.netflix.spinnaker.keel.event.BeforeAssetUpsertEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher

class MemoryAssetRepository(
  private val applicationEventPublisher: ApplicationEventPublisher
) : AssetRepository {

  private val log = LoggerFactory.getLogger(javaClass)

  private val assets: MutableMap<String, Asset<AssetSpec>> = mutableMapOf()

  init {
    log.info("Using ${javaClass.simpleName}")
  }

  override fun upsertAsset(asset: Asset<AssetSpec>): Asset<AssetSpec> {
    applicationEventPublisher.publishEvent(BeforeAssetUpsertEvent(asset))
    assets.put(asset.id(), asset)
    applicationEventPublisher.publishEvent(AfterAssetUpsertEvent(asset))
    return asset
  }

  override fun getAssets() = assets.values.toList()

  override fun getAssets(statuses: List<AssetStatus>)
    = assets.values.filter { statuses.contains(it.status) }.toList()

  override fun getAsset(id: String) = assets[id]

  override fun deleteAsset(id: String, preserveHistory: Boolean) {
    if (preserveHistory) {
      getAsset(id)?.status = AssetStatus.ABSENT
    } else {
      assets.remove(id)
    }
  }
}
