/*
 * Copyright 2017 Netflix, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.cache

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Tag
import com.netflix.spinnaker.cats.redis.cache.RedisCache

class SpectatorRedisCacheMetrics implements RedisCache.CacheMetrics {
  private final Registry registry

  SpectatorRedisCacheMetrics(Registry registry) {
    this.registry = registry
  }

  @Override
  void merge(String prefix, String type,
             int itemCount, int keysWritten, int relationshipCount, int hashMatches,
             int hashUpdates, int saddOperations, int msetOperations, int hmsetOperations,
             int pipelineOperations, int expireOperations) {
    final Iterable<Tag> tags = tags(prefix, type)
    registry.counter(id("cats.redisCache.merge", "itemCount", tags)).increment(itemCount)
    registry.counter(id("cats.redisCache.merge", "keysWritten", tags)).increment(keysWritten)
    registry.counter(id("cats.redisCache.merge", "relationshipCount", tags)).increment(relationshipCount)
    registry.counter(id("cats.redisCache.merge", "hashMatches", tags)).increment(hashMatches)
    registry.counter(id("cats.redisCache.merge", "hashUpdates", tags)).increment(hashUpdates)
    registry.counter(id("cats.redisCache.merge", "saddOperations", tags)).increment(saddOperations)
    registry.counter(id("cats.redisCache.merge", "msetOperations", tags)).increment(msetOperations)
    registry.counter(id("cats.redisCache.merge", "hmsetOperations", tags)).increment(hmsetOperations)
    registry.counter(id("cats.redisCache.merge", "pipelineOperations", tags)).increment(pipelineOperations)
    registry.counter(id("cats.redisCache.merge", "expireOperations", tags)).increment(expireOperations)
  }

  @Override
  void evict(String prefix, String type,
             int itemCount, int keysDeleted, int hashesDeleted, int delOperations,
             int hdelOperations, int sremOperations) {
    final Iterable<Tag> tags = tags(prefix, type)
    registry.counter(id("cats.redisCache.evict", "itemCount", tags)).increment(itemCount)
    registry.counter(id("cats.redisCache.evict", "keysDeleted", tags)).increment(keysDeleted)
    registry.counter(id("cats.redisCache.evict", "hashesDeleted", tags)).increment(hashesDeleted)
    registry.counter(id("cats.redisCache.evict", "delOperations", tags)).increment(delOperations)
    registry.counter(id("cats.redisCache.evict", "hdelOperations", tags)).increment(hdelOperations)
    registry.counter(id("cats.redisCache.evict", "sremOperations", tags)).increment(sremOperations)
  }

  @Override
  void get(String prefix, String type,
           int itemCount, int requestedSize, int keysRequested,
           int relationshipsRequested, int mgetOperations) {
    final Iterable<Tag> tags = tags(prefix, type)
    registry.counter(id("cats.redisCache.get", "itemCount", tags)).increment(itemCount)
    registry.counter(id("cats.redisCache.get", "requestedSize", tags)).increment(keysRequested)
    registry.counter(id("cats.redisCache.get", "keysRequested", tags)).increment(keysRequested)
    registry.counter(id("cats.redisCache.get", "relationshipsRequested", tags)).increment(relationshipsRequested)
    registry.counter(id("cats.redisCache.get", "mgetOperations", tags)).increment(mgetOperations)
  }

  private Id id(String metricGroup, String metric, Iterable<Tag> tags) {
    return registry.createId(metricGroup + '.' + metric, tags)
  }

  private Iterable<Tag> tags(String prefix, String type) {
    return [new BasicTag("prefix", prefix), new BasicTag("type", type)]
  }
}
