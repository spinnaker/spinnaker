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
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.PostConstruct

class MemoryIntentActivityRepository : IntentActivityRepository {

  private val log = LoggerFactory.getLogger(javaClass)

  private val currentOrchestrations: ConcurrentHashMap<String, MutableSet<String>> = ConcurrentHashMap()

  private val orchestrations: ConcurrentHashMap<String, MutableSet<String>> = ConcurrentHashMap()

  @PostConstruct
  fun init() {
    log.info("Using ${javaClass.simpleName}")
  }

  override fun addOrchestration(intentId: String, orchestrationId: String) {
    val orchestrationUUID = parseOrchestrationId(orchestrationId)
    if (!orchestrations.containsKey(intentId)) {
      orchestrations[intentId] = mutableSetOf()
    }
    if (orchestrations[intentId]?.contains(orchestrationUUID) == false) {
      orchestrations[intentId]?.add(orchestrationUUID)
      if (!currentOrchestrations.containsKey(intentId)) {
        currentOrchestrations.put(intentId, mutableSetOf())
      }
      currentOrchestrations[intentId]?.add(orchestrationUUID)
    }
  }

  override fun addOrchestrations(intentId: String, orchestrations: List<String>) {
    orchestrations.forEach { addOrchestration(intentId, it) }
  }

  override fun getCurrent(intentId: String): List<String> {
    return currentOrchestrations.getOrDefault(intentId, listOf<String>()).toList()
  }

  override fun upsertCurrent(intentId: String, orchestrations: List<String>) {
    if (!currentOrchestrations.containsKey(intentId)) {
      currentOrchestrations.put(intentId, mutableSetOf())
    }
    currentOrchestrations[intentId]?.addAll(orchestrations.toMutableSet())
  }

  override fun upsertCurrent(intentId: String, orchestration: String) {
    upsertCurrent(intentId, listOf(orchestration))
  }

  override fun removeCurrent(intentId: String, orchestrationId: String) {
    currentOrchestrations[intentId]?.remove(orchestrationId)
  }

  override fun removeCurrent(intentId: String) {
    currentOrchestrations.remove(intentId)
  }

  override fun getHistory(intentId: String) = orchestrations.getOrDefault(intentId, listOf<String>()).toList()

}
