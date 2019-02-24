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

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Tag

class SpectatorSqlCacheMetrics(
  private val registry: Registry
) : SqlCacheMetrics {

  override fun merge(prefix: String,
                     type: String,
                     itemCount: Int,
                     itemsStored: Int,
                     relationshipCount: Int,
                     relationshipsStored: Int,
                     selectOperations: Int,
                     writeOperations: Int,
                     deleteOperations: Int) {
    val tags = tags(prefix, type)
    registry.counter(id("cats.sqlCache.merge", "itemCount", tags)).increment(itemCount.toLong())
    registry.counter(id("cats.sqlCache.merge", "itemsStored", tags)).increment(itemsStored.toLong())
    registry.counter(id("cats.sqlCache.merge", "relationshipCount", tags)).increment(relationshipCount.toLong())
    registry.counter(id("cats.sqlCache.merge", "relationshipsStored", tags)).increment(relationshipsStored.toLong())
    registry.counter(id("cats.sqlCache.merge", "selectOperations", tags)).increment(selectOperations.toLong())
    registry.counter(id("cats.sqlCache.merge", "writeOperations", tags)).increment(writeOperations.toLong())
    registry.counter(id("cats.sqlCache.merge", "deleteOperations", tags)).increment(deleteOperations.toLong())
  }

  override fun evict(prefix: String,
                     type: String,
                     itemCount: Int,
                     itemsDeleted: Int,
                     deleteOperations: Int) {
    val tags = tags(prefix, type)
    registry.counter(id("cats.sqlCache.evict", "itemCount", tags)).increment(itemCount.toLong())
    registry.counter(id("cats.sqlCache.evict", "itemsDeleted", tags)).increment(itemsDeleted.toLong())
    registry.counter(id("cats.sqlCache.evict", "deleteOperations", tags)).increment(deleteOperations.toLong())
    super.evict(prefix, type, itemCount, itemsDeleted, deleteOperations)
  }

  override fun get(prefix: String,
                   type: String,
                   itemCount: Int,
                   requestedSize: Int,
                   relationshipsRequested: Int,
                   selectOperations: Int,
                   async: Boolean) {
    val tags = tags(prefix, type)
    registry.counter(id("cats.sqlCache.get", "itemCount", tags)).increment(itemCount.toLong())
    registry.counter(id("cats.sqlCache.get", "requestedSize", tags)).increment(requestedSize.toLong())
    registry.counter(id("cats.sqlCache.get", "relationshipsRequested", tags)).increment(relationshipsRequested.toLong())
    registry.counter(id("cats.sqlCache.get", "selectOperations", tags)).increment(selectOperations.toLong())
  }

  private fun id(metricGroup: String, metric: String, tags: Iterable<Tag>) =
    registry.createId("$metricGroup.$metric", tags)

  private fun tags(prefix: String, type: String, async: Boolean = false) =
    listOf(BasicTag("prefix", prefix), BasicTag("type", type), BasicTag("async", async.toString()))
}
