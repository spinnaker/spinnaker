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

import com.netflix.spinnaker.keel.IntentActivityRepository
import org.slf4j.LoggerFactory
import javax.annotation.PostConstruct

class MemoryIntentActivityRepository : IntentActivityRepository {

  private val log = LoggerFactory.getLogger(javaClass)

  private val orchestrations: MutableMap<String, MutableList<String>> = mutableMapOf()

  @PostConstruct
  fun init() {
    log.info("Using ${javaClass.simpleName}")
  }

  override fun addOrchestration(intentId: String, orchestrationId: String) {
    if (!orchestrations.containsKey(intentId)) {
      orchestrations[intentId] = mutableListOf()
    }
    if (orchestrations[intentId]?.contains(orchestrationId) == false) {
      orchestrations[intentId]?.add(orchestrationId)
    }
  }

  override fun addOrchestrations(intentId: String, orchestrations: List<String>) {
    orchestrations.forEach { addOrchestration(intentId, it) }
  }

  override fun getHistory(intentId: String) = orchestrations.getOrDefault(intentId, listOf<String>()).toList()
}
