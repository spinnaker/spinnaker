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

import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.model.fingerprint
import com.netflix.spinnaker.keel.persistence.AssetRepository
import com.netflix.spinnaker.keel.persistence.AssetState.Diff
import com.netflix.spinnaker.keel.persistence.AssetState.Missing
import com.netflix.spinnaker.keel.persistence.AssetState.Ok
import com.netflix.spinnaker.q.MessageHandler
import com.netflix.spinnaker.q.Queue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.coroutines.experimental.SequenceBuilder
import kotlin.coroutines.experimental.buildSequence

@Component
class ValidateAssetTreeHandler(
  private val repository: AssetRepository,
  private val assetService: AssetService,
  override val queue: Queue
) : MessageHandler<ValidateAssetTree> {

  override val messageType = ValidateAssetTree::class.java

  override fun handle(message: ValidateAssetTree) {
    val invalidAssetIds = buildSequence {
      validateSubTree(message.rootId)
    }
    invalidAssetIds.forEach { id ->
      log.debug("{} : Requesting convergence", id)
      queue.push(ConvergeAsset(id))
    }
  }

  private val log = LoggerFactory.getLogger(javaClass)

  private suspend fun SequenceBuilder<AssetId>.validateSubTree(id: AssetId) {
    val desired = repository.getContainer(id)
    if (desired.asset == null) {
      log.error("{} : Not found", id)
    } else {
      log.debug("{} : Validating state", id)
      assetService
        .current(desired)
        .also { assetContainer ->
          when {
            assetContainer.current == null -> {
              log.info("{}: Does not exist", id)
              repository.updateState(id, Missing)
              yield(id)
            }
            desired.asset.fingerprint == assetContainer.current.fingerprint -> {
              log.info("{} : Current state valid", id)
              repository.updateState(id, Ok)
            }
            else -> {
              log.info("{} : Current state invalid", id)
              repository.updateState(id, Diff)
              yield(id)
            }
          }
          repository.dependents(id).forEach { dependentId ->
            log.debug("{} : Validating dependent asset {}", id, dependentId)
            validateSubTree(dependentId)
          }
        }
    }
  }
}
