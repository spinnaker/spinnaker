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

import com.netflix.spinnaker.keel.api.AssetName
import com.netflix.spinnaker.keel.api.fingerprint
import com.netflix.spinnaker.keel.persistence.AssetRepository
import com.netflix.spinnaker.keel.persistence.AssetState.Diff
import com.netflix.spinnaker.keel.persistence.AssetState.Missing
import com.netflix.spinnaker.keel.persistence.AssetState.Ok
import com.netflix.spinnaker.keel.plugin.PluginRequestFailed
import com.netflix.spinnaker.q.MessageHandler
import com.netflix.spinnaker.q.Queue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ValidateAssetTreeHandler(
  private val repository: AssetRepository,
  private val assetService: AssetService,
  override val queue: Queue
) : MessageHandler<ValidateAsset> {

  override val messageType = ValidateAsset::class.java

  override fun handle(message: ValidateAsset) {
    val invalidAssetNames = sequence<AssetName> {
      validateSubTree(message.name)
    }
    invalidAssetNames.forEach { name ->
      log.debug("{} : Requesting convergence", name)
      queue.push(ConvergeAsset(name))
    }
  }

  private val log = LoggerFactory.getLogger(javaClass)

  private suspend fun SequenceScope<AssetName>.validateSubTree(name: AssetName) {
    val desired = repository.get(name)
    if (desired == null) {
      log.error("{} : Not found", name)
    } else {
      log.debug("{} : Validating state", name)
      try {
        assetService
          .current(desired)
          .also { assetPair ->
            when {
              assetPair.current == null -> {
                log.info("{}: Does not exist", name)
                repository.updateState(name, Missing)
                yield(name)
              }
              assetPair.desired.fingerprint == assetPair.current.fingerprint -> {
                log.info("{} : Current state valid", name)
                repository.updateState(name, Ok)
              }
              else -> {
                log.info("{} : Current state invalid", name)
                log.info("Desired state:\n{}\n\nDoes not match:\n\n{}", assetPair.desired, assetPair.current)
                repository.updateState(name, Diff)
                yield(name)
              }
            }
          }
      } catch (e: PluginRequestFailed) {
        log.error(e.message)
      }
    }
  }
}
