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

import com.netflix.spinnaker.keel.grpc.PluginRequestFailed
import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.persistence.AssetRepository
import com.netflix.spinnaker.keel.persistence.AssetState
import com.netflix.spinnaker.keel.persistence.AssetState.Ok
import com.netflix.spinnaker.keel.persistence.AssetState.Unknown
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
    repository.getContainer(message.id)?.also { assetContainer ->
      val asset = assetContainer.asset

      val outdatedDependencies = asset.outdatedDependencies
      if (outdatedDependencies.isEmpty()) {
        try {
          if (vetoService.allow(assetContainer)) {
            log.info("{} : requesting convergence", asset.id)
            assetService.converge(assetContainer)
          } else {
            log.info("{} : convergence was vetoed", asset.id)
          }
        } catch (e: PluginRequestFailed) {
          log.error(e.message)
        }
      } else {
        if (log.isInfoEnabled) {
          log.info("{} : not converging as outdated dependencies were found:", asset.id)
          outdatedDependencies.forEach { (id, state) ->
            log.info(" • {} : {}", state, id)
          }
        }
      }
    }
  }

  private val Asset.outdatedDependencies: Collection<Pair<AssetId, AssetState>>
    get() =
      dependsOn.flatMap { dependencyId ->
        val state = repository.lastKnownState(dependencyId)?.first ?: Unknown
        return if (state == Ok) {
          repository.get(dependencyId)?.outdatedDependencies
            ?: listOf(dependencyId to Unknown)
        } else {
          listOf(dependencyId to state)
        }
      }
}
