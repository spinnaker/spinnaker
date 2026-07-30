/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.cats.sql.cache

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag

class SpectatorSqlCacheMetrics(
  private val registry: MeterRegistry
) : SqlCacheMetrics {

  override fun merge(
    prefix: String,
    type: String,
    itemCount: Int,
    itemsStored: Int,
    relationshipCount: Int,
    relationshipsStored: Int,
    selectOperations: Int,
    writeOperations: Int,
    deleteOperations: Int,
    duplicates: Int
  ) {
    val tags = tags(prefix, type)
    registry.counter(id("cats.sqlCache.merge", "itemCount"), tags).increment(itemCount.toDouble())
    registry.counter(id("cats.sqlCache.merge", "itemsStored"), tags).increment(itemsStored.toDouble())
    registry.counter(id("cats.sqlCache.merge", "relationshipCount"), tags).increment(relationshipCount.toDouble())
    registry.counter(id("cats.sqlCache.merge", "relationshipsStored"), tags).increment(relationshipsStored.toDouble())
    registry.counter(id("cats.sqlCache.merge", "selectOperations"), tags).increment(selectOperations.toDouble())
    registry.counter(id("cats.sqlCache.merge", "writeOperations"), tags).increment(writeOperations.toDouble())
    registry.counter(id("cats.sqlCache.merge", "deleteOperations"), tags).increment(deleteOperations.toDouble())
    registry.counter(id("cats.sqlCache.merge", "duplicates"), tags).increment(duplicates.toDouble())
  }

  override fun evict(
    prefix: String,
    type: String,
    itemCount: Int,
    itemsDeleted: Int,
    deleteOperations: Int
  ) {
    val tags = tags(prefix, type)
    registry.counter(id("cats.sqlCache.evict", "itemCount"), tags).increment(itemCount.toDouble())
    registry.counter(id("cats.sqlCache.evict", "itemsDeleted"), tags).increment(itemsDeleted.toDouble())
    registry.counter(id("cats.sqlCache.evict", "deleteOperations"), tags).increment(deleteOperations.toDouble())
    super.evict(prefix, type, itemCount, itemsDeleted, deleteOperations)
  }

  override fun get(
    prefix: String,
    type: String,
    itemCount: Int,
    requestedSize: Int,
    relationshipsRequested: Int,
    selectOperations: Int,
    async: Boolean
  ) {
    val tags = tags(prefix, type, async)
    registry.counter(id("cats.sqlCache.get", "itemCount"), tags).increment(itemCount.toDouble())
    registry.counter(id("cats.sqlCache.get", "requestedSize"), tags).increment(requestedSize.toDouble())
    registry.counter(id("cats.sqlCache.get", "relationshipsRequested"), tags).increment(relationshipsRequested.toDouble())
    registry.counter(id("cats.sqlCache.get", "selectOperations"), tags).increment(selectOperations.toDouble())
  }

  private fun id(metricGroup: String, metric: String) = "$metricGroup.$metric"

  private fun tags(prefix: String, type: String, async: Boolean = false) =
    listOf(Tag.of("prefix", prefix), Tag.of("type", type), Tag.of("async", async.toString()))
}
