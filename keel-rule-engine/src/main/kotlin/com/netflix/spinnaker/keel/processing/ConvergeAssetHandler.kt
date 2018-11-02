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
package com.netflix.spinnaker.keel.processing

import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.persistence.AssetRepository
import com.netflix.spinnaker.keel.plugin.PluginRequestFailed
import com.netflix.spinnaker.q.MessageHandler
import com.netflix.spinnaker.q.Queue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ConvergeAssetHandler(
  private val repository: AssetRepository,
  override val queue: Queue,
  private val assetService: AssetService,
  private val vetoService: VetoService
) : MessageHandler<ConvergeAsset> {

  override val messageType = ConvergeAsset::class.java
  private val log = LoggerFactory.getLogger(javaClass)

  override fun handle(message: ConvergeAsset) {
    repository.get(message.name)?.also { asset ->
      try {
        if (vetoService.allow(asset)) {
          log.info("{} : requesting convergence", asset.id)
          assetService.converge(asset)
        } else {
          log.info("{} : convergence was vetoed", asset.id)
        }
      } catch (e: PluginRequestFailed) {
        log.error(e.message)
      }
    }
  }
}
