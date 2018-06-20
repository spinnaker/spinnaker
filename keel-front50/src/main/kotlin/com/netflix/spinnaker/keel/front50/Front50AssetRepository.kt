/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.keel.front50

import com.google.common.util.concurrent.RateLimiter
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.Front50RateLimitProperties
import com.netflix.spinnaker.keel.Asset
import com.netflix.spinnaker.keel.AssetRepository
import com.netflix.spinnaker.keel.AssetSpec
import com.netflix.spinnaker.keel.AssetStatus
import com.netflix.spinnaker.keel.event.AfterAssetDeleteEvent
import com.netflix.spinnaker.keel.event.AfterAssetUpsertEvent
import com.netflix.spinnaker.keel.event.BeforeAssetDeleteEvent
import com.netflix.spinnaker.keel.event.BeforeAssetUpsertEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnExpression("\${front50.assetRepositoryEnabled:true}")
class Front50AssetRepository(
  private val front50Service: Front50Service,
  private val registry: Registry,
  private val applicationEventPublisher: ApplicationEventPublisher,
  front50RateLimitProperties: Front50RateLimitProperties
): AssetRepository {

  private val log = LoggerFactory.getLogger(javaClass)

  private val rateLimitTimeId = registry.createId("front50.rateLimitTimeSeconds")

  private val rateLimiter = RateLimiter.create(front50RateLimitProperties.requestsPerSecond)

  init {
    log.info("Using ${javaClass.simpleName}")
  }

  override fun upsertAsset(asset: Asset<AssetSpec>): Asset<AssetSpec> {
    applicationEventPublisher.publishEvent(BeforeAssetUpsertEvent(asset))
    return rateLimited {
      front50Service.upsertAsset(asset)
    }.also {
      applicationEventPublisher.publishEvent(AfterAssetUpsertEvent(it))
    }
  }

  override fun getAssets() = rateLimited { front50Service.getAssets() }

  override fun getAssets(statuses: List<AssetStatus>) = rateLimited { front50Service.getAssetsByStatus(statuses) }

  override fun getAsset(id: String) = rateLimited { front50Service.getAsset(id) }

  @Suppress("IMPLICIT_CAST_TO_ANY")
  override fun deleteAsset(id: String, preserveHistory: Boolean) {
    rateLimited {
      getAsset(id).also { asset ->
        applicationEventPublisher.publishEvent(BeforeAssetDeleteEvent(asset))
        if (preserveHistory) {
          asset.status = AssetStatus.INACTIVE
          upsertAsset(asset)
        } else {
          front50Service.deleteAsset(id)
        }
        applicationEventPublisher.publishEvent(AfterAssetDeleteEvent(asset))
      }
    }
  }

  private inline fun <reified T> rateLimited(op: () -> T): T =
    rateLimiter.acquire().let { secondsBlocked ->
      registry.timer(rateLimitTimeId).record((secondsBlocked * 1000).toLong(), TimeUnit.MILLISECONDS)
      op.invoke()
    }
}
