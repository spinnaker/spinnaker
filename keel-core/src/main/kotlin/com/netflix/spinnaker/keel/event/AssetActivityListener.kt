/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.keel.event

import com.netflix.spinnaker.keel.AssetActivityRepository
import com.netflix.spinnaker.keel.AssetChangeAction.DELETE
import com.netflix.spinnaker.keel.AssetChangeAction.UPSERT
import com.netflix.spinnaker.keel.AssetChangeRecord
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class AssetActivityListener(
  private val assetActivityRepository: AssetActivityRepository
) {

  @EventListener(AfterAssetUpsertEvent::class, AfterAssetDeleteEvent::class)
  fun recordUpsert(event: AssetAwareEvent) {
    // TODO rz - actor needs to be pulled out of AuthenticatedRequest
    assetActivityRepository.record(AssetChangeRecord(
      event.asset.id(),
      action = if (event is AfterAssetUpsertEvent) UPSERT else DELETE,
      actor = "TODO: unknown",
      value = event.asset
    ))
  }
}
