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
package com.netflix.spinnaker.cats.redis.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.redis.RedisClientDelegate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RedisCache extends AbstractRedisCache {

  public RedisCache(String prefix, RedisClientDelegate redisClientDelegate, ObjectMapper objectMapper, RedisCacheOptions options, CacheMetrics cacheMetrics) {
    super(prefix, redisClientDelegate, objectMapper, options, cacheMetrics);
  }

  @Override
  protected void mergeItems(String type, Collection<CacheData> items) {
    if (items.isEmpty()) {
      return;
    }
    final Set<String> relationshipNames = new HashSet<>();
    final List<String> keysToSet = new LinkedList<>();
    final Set<String> idSet = new HashSet<>();

    final Map<String, Integer> ttlSecondsByKey = new HashMap<>();
    int skippedWrites = 0;

    final Map<String, String> hashes = getHashes(type, items);

    final NavigableMap<String, String> updatedHashes = new TreeMap<>();

    for (CacheData item : items) {
      MergeOp op = buildMergeOp(type, item, hashes);
      relationshipNames.addAll(op.relNames);
      keysToSet.addAll(op.keysToSet);
      idSet.add(item.getId());
      updatedHashes.putAll(op.hashesToSet);
      skippedWrites += op.skippedWrites;

      if (item.getTtlSeconds() > 0) {
        for (String key : op.keysToSet) {
          ttlSecondsByKey.put(key, item.getTtlSeconds());
        }
      }
    }

    AtomicInteger saddOperations = new AtomicInteger();
    AtomicInteger msetOperations = new AtomicInteger();
    AtomicInteger hmsetOperations = new AtomicInteger();
    AtomicInteger pipelineOperations = new AtomicInteger();
    AtomicInteger expireOperations = new AtomicInteger();
    if (keysToSet.size() > 0) {
      redisClientDelegate.withMultiKeyPipeline(pipeline -> {
        for (List<String> idPart : Iterables.partition(idSet, options.getMaxSaddSize())) {
          final String[] ids = idPart.toArray(new String[idPart.size()]);
          pipeline.sadd(allOfTypeReindex(type), ids);
          saddOperations.incrementAndGet();
          pipeline.sadd(allOfTypeId(type), ids);
          saddOperations.incrementAndGet();
        }

        for (List<String> keys : Lists.partition(keysToSet, options.getMaxMsetSize())) {
          pipeline.mset(keys.toArray(new String[keys.size()]));
          msetOperations.incrementAndGet();
        }

        if (!relationshipNames.isEmpty()) {
          for (List<String> relNamesPart : Iterables.partition(relationshipNames, options.getMaxSaddSize())) {
            pipeline.sadd(allRelationshipsId(type), relNamesPart.toArray(new String[relNamesPart.size()]));
            saddOperations.incrementAndGet();
          }
        }

        if (!updatedHashes.isEmpty()) {
          for (List<String> hashPart : Iterables.partition(updatedHashes.keySet(), options.getMaxHmsetSize())) {
            pipeline.hmset(hashesId(type), updatedHashes.subMap(hashPart.get(0), true, hashPart.get(hashPart.size() - 1), true));
            hmsetOperations.incrementAndGet();
          }
        }
        pipeline.sync();
        pipelineOperations.incrementAndGet();
      });

      redisClientDelegate.withMultiKeyPipeline(pipeline -> {
        for (List<Map.Entry<String, Integer>> ttlPart : Iterables.partition(ttlSecondsByKey.entrySet(), options.getMaxPipelineSize())) {
          for (Map.Entry<String, Integer> ttlEntry : ttlPart) {
            pipeline.expire(ttlEntry.getKey(), ttlEntry.getValue());
          }
          expireOperations.addAndGet(ttlPart.size());
          pipeline.sync();
          pipelineOperations.incrementAndGet();
        }
      });
    }

    cacheMetrics.merge(
      prefix,
      type,
      items.size(),
      keysToSet.size() / 2,
      relationshipNames.size(),
      skippedWrites,
      updatedHashes.size(),
      saddOperations.get(),
      0,
      msetOperations.get(),
      hmsetOperations.get(),
      pipelineOperations.get(),
      expireOperations.get(),
      0
    );
  }

  @Override
  protected void evictItems(String type, List<String> identifiers, Collection<String> allRelationships) {
    List<String> delKeys = new ArrayList<>((allRelationships.size() + 1) * identifiers.size());
    for (String id : identifiers) {
      for (String relationship : allRelationships) {
        delKeys.add(relationshipId(type, id, relationship));
      }
      delKeys.add(attributesId(type, id));
    }

    AtomicInteger delOperations = new AtomicInteger();
    AtomicInteger hdelOperations = new AtomicInteger();
    AtomicInteger sremOperations = new AtomicInteger();
    redisClientDelegate.withMultiKeyPipeline(pipeline -> {
      for (List<String> delPartition : Lists.partition(delKeys, options.getMaxDelSize())) {
        pipeline.del(delPartition.toArray(new String[delPartition.size()]));
        delOperations.incrementAndGet();
        pipeline.hdel(hashesId(type), delPartition.toArray(new String[delPartition.size()]));
        hdelOperations.incrementAndGet();
      }

      for (List<String> idPartition : Lists.partition(identifiers, options.getMaxDelSize())) {
        String[] ids = idPartition.toArray(new String[idPartition.size()]);
        pipeline.srem(allOfTypeId(type), ids);
        sremOperations.incrementAndGet();
        pipeline.srem(allOfTypeReindex(type), ids);
        sremOperations.incrementAndGet();
      }

      pipeline.sync();
    });

    cacheMetrics.evict(
      prefix,
      type,
      identifiers.size(),
      delKeys.size(),
      delKeys.size(),
      delOperations.get(),
      hdelOperations.get(),
      sremOperations.get()
    );
  }
}
