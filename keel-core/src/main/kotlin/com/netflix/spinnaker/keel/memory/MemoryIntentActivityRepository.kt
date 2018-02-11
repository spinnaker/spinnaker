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

import com.netflix.spinnaker.config.KeelProperties
import com.netflix.spinnaker.keel.IntentActivityRepository
import com.netflix.spinnaker.keel.IntentConvergenceRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.PostConstruct

class MemoryIntentActivityRepository
@Autowired constructor(
  private val keelProperties: KeelProperties
) : IntentActivityRepository {

  private val log = LoggerFactory.getLogger(javaClass)

  private val orchestrations: ConcurrentHashMap<String, MutableSet<String>> = ConcurrentHashMap()

  private val convergenceLog: ConcurrentHashMap<String, MutableList<IntentConvergenceRecord>> = ConcurrentHashMap()

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
    }
  }

  override fun addOrchestrations(intentId: String, orchestrations: List<String>) {
    orchestrations.forEach { addOrchestration(intentId, it) }
  }

  override fun getHistory(intentId: String) = orchestrations.getOrDefault(intentId, mutableSetOf()).toList()

  override fun logConvergence(intentConvergenceRecord: IntentConvergenceRecord) {
    val intentId = intentConvergenceRecord.intentId
    if (!convergenceLog.containsKey(intentId)){
      convergenceLog[intentId] = mutableListOf(intentConvergenceRecord)
    } else {
      if (convergenceLog[intentId] == null) {
        convergenceLog[intentId] = mutableListOf(intentConvergenceRecord)
      } else {
        convergenceLog[intentId]?.let { l ->
          l.add(intentConvergenceRecord)
          // Drop oldest entries if we're over the message limit
          val numMsgsLeft = keelProperties.maxConvergenceLogEntriesPerIntent - l.count()
          if (numMsgsLeft < 0){
            convergenceLog[intentId] = l.drop(-1*numMsgsLeft).toMutableList()
          }
        }
      }
    }
  }

  override fun getLog(intentId: String): List<IntentConvergenceRecord>
    = convergenceLog[intentId] ?: emptyList()

  // if there are multiple messages with the same timestamp, return the first
  override fun getLogEntry(intentId: String, timestampMillis: Long)
    = convergenceLog[intentId]?.filter { it.timestampMillis == timestampMillis }?.toList()?.also {
      // The same intent shouldn't be processed more than once at the exact same time.
      if (it.size > 1) log.warn("Two messages with the same timestampMillis. This shouldn't happen.")
    }?.first()
}
