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

import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.persistence.DiffFingerprintRepository
import java.time.Clock
import java.time.Instant

class InMemoryDiffFingerprintRepository(
  private val clock: Clock = Clock.systemUTC()
) : DiffFingerprintRepository {
  private val hashes: MutableMap<String, Record> = mutableMapOf()

  override fun store(entityId: String, diff: ResourceDiff<*>) {
    val existing = hashes[entityId]
    val hash = diff.generateHash()

    if (existing != null && hash == existing.hash) {
      hashes[entityId] = existing.copy(count = existing.count + 1)
    } else {
      hashes[entityId] = Record(hash, clock.instant())
    }
  }

  override fun diffCount(entityId: String): Int =
    hashes[entityId]?.count ?: 0

  override fun seen(entityId: String, diff: ResourceDiff<*>): Boolean =
    hashes[entityId]?.hash == diff.generateHash()

  override fun clear(entityId: String) {
    hashes.remove(entityId)
  }

  private data class Record(
    val hash: String,
    val timestamp: Instant, // timestamp when diff was first seen
    val count: Int = 1
  )

  fun flush() {
    hashes.clear()
  }
}
