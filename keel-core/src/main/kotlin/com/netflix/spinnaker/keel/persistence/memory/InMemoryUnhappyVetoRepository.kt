/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.persistence.UnhappyVetoRepository
import java.time.Clock
import java.time.Instant

class InMemoryUnhappyVetoRepository(
  override val clock: Clock
) : UnhappyVetoRepository(clock) {

  private val resources: MutableMap<String, Record> = mutableMapOf()

  override fun markUnhappyForWaitingTime(resourceId: String, application: String) {
    resources[resourceId] = Record(application, calculateExpirationTime())
  }

  override fun markHappy(resourceId: String) {
    resources.remove(resourceId)
  }

  override fun getVetoStatus(resourceId: String): UnhappyVetoStatus {
    val record = resources[resourceId] ?: return UnhappyVetoStatus()
    return UnhappyVetoStatus(
      shouldSkip = record.recheckTime > clock.instant(),
      shouldRecheck = record.recheckTime < clock.instant()
    )
  }

  override fun getAll(): Set<String> {
    val now = clock.instant()
    return resources.filter { it.value.recheckTime > now }.keys.toSet()
  }

  override fun getAllForApp(application: String): Set<String> {
    val now = clock.instant()
    return resources.filter { (_, record) ->
      record.recheckTime > now && record.application == application
    }.keys.toSet()
  }

  fun flush() = resources.clear()

  private data class Record(
    val application: String,
    val recheckTime: Instant
  )
}
