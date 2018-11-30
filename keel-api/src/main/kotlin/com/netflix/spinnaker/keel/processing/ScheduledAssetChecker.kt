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
import com.netflix.spinnaker.q.Queue
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ScheduledAssetChecker(
  private val repository: AssetRepository,
  private val queue: Queue
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @Scheduled(fixedDelayString = "\${check.cycle.frequency.ms:60000}")
  fun runCheckCycle() {
    log.info("Starting check cycle")
    repository.allAssets {
      queue.push(ValidateAsset(it.id))
    }
  }
}
