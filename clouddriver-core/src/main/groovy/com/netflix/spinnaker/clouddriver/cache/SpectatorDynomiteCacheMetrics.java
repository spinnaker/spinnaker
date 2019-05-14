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
package com.netflix.spinnaker.clouddriver.cache;

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import com.netflix.spinnaker.cats.dynomite.cache.DynomiteCache.CacheMetrics;
import java.util.Arrays;

public class SpectatorDynomiteCacheMetrics implements CacheMetrics {

  private final Registry registry;

  SpectatorDynomiteCacheMetrics(Registry registry) {
    this.registry = registry;
  }

  @Override
  public void merge(
      String prefix,
      String type,
      int itemCount,
      int relationshipCount,
      int hashMatches,
      int hashUpdates,
      int saddOperations,
      int hmsetOperations,
      int expireOperations,
      int delOperations) {
    final Iterable<Tag> tags = tags(prefix, type);
    registry.counter(id("cats.dynomiteCache.merge", "itemCount", tags)).increment(itemCount);
    registry
        .counter(id("cats.dynomiteCache.merge", "relationshipCount", tags))
        .increment(relationshipCount);
    registry.counter(id("cats.dynomiteCache.merge", "hashMatches", tags)).increment(hashMatches);
    registry.counter(id("cats.dynomiteCache.merge", "hashUpdates", tags)).increment(hashUpdates);
    registry
        .counter(id("cats.dynomiteCache.merge", "saddOperations", tags))
        .increment(saddOperations);
    registry
        .counter(id("cats.dynomiteCache.merge", "hmsetOperations", tags))
        .increment(hmsetOperations);
    registry
        .counter(id("cats.dynomiteCache.merge", "expireOperations", tags))
        .increment(expireOperations);
    registry
        .counter(id("cats.dynomiteCache.merge", "delOperations", tags))
        .increment(delOperations);
  }

  @Override
  public void evict(
      String prefix, String type, int itemCount, int delOperations, int sremOperations) {
    final Iterable<Tag> tags = tags(prefix, type);
    registry.counter(id("cats.dynomiteCache.evict", "itemCount", tags)).increment(itemCount);
    registry
        .counter(id("cats.dynomiteCache.evict", "delOperations", tags))
        .increment(delOperations);
    registry
        .counter(id("cats.dynomiteCache.evict", "sremOperations", tags))
        .increment(sremOperations);
  }

  @Override
  public void get(
      String prefix,
      String type,
      int itemCount,
      int requestedSize,
      int relationshipsRequested,
      int hmgetAllOperations) {
    final Iterable<Tag> tags = tags(prefix, type);
    registry.counter(id("cats.dynomiteCache.get", "itemCount", tags)).increment(itemCount);
    registry.counter(id("cats.dynomiteCache.get", "requestedSize", tags)).increment(requestedSize);
    registry
        .counter(id("cats.dynomiteCache.get", "relationshipsRequested", tags))
        .increment(relationshipsRequested);
    registry
        .counter(id("cats.dynomiteCache.get", "hmgetAllOperations", tags))
        .increment(hmgetAllOperations);
  }

  private Id id(String metricGroup, String metric, Iterable<Tag> tags) {
    return registry.createId(metricGroup + '.' + metric, tags);
  }

  private Iterable<Tag> tags(String prefix, String type) {
    return Arrays.asList(new BasicTag("prefix", prefix), new BasicTag("type", type));
  }
}
