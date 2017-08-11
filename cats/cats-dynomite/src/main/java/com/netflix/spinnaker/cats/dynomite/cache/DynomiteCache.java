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
package com.netflix.spinnaker.cats.dynomite.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.netflix.dyno.connectionpool.exception.DynoException;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.dynomite.DynomiteClientDelegate;
import com.netflix.spinnaker.cats.redis.cache.AbstractRedisCache;
import com.netflix.spinnaker.cats.redis.cache.RedisCacheOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.exceptions.JedisException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class DynomiteCache extends AbstractRedisCache {

  // Arbitrary selection of hash expiration TTL. While the try/catches in a mergeItems call should catch cache drift
  // caused by exceptions, this will ensure that any missed stale caches are no older than a few hours.
  private final static int HASH_EXPIRY_SECONDS = (int) Duration.ofHours(3).getSeconds();

  private final Logger log = LoggerFactory.getLogger(getClass());

  public DynomiteCache(String prefix, DynomiteClientDelegate dynomiteClientDelegate, ObjectMapper objectMapper, RedisCacheOptions options, CacheMetrics cacheMetrics) {
    super(prefix, dynomiteClientDelegate, objectMapper, options, cacheMetrics);
  }

  @Override
  public void mergeItems(String type, Collection<CacheData> items) {
    if (items.isEmpty()){
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
    AtomicInteger setOperations = new AtomicInteger();
    AtomicInteger msetOperations = new AtomicInteger();
    AtomicInteger hmsetOperations = new AtomicInteger();
    AtomicInteger expireOperations = new AtomicInteger();
    if (!keysToSet.isEmpty()) {
      final Set<String> failedKeys = new HashSet<>();
      redisClientDelegate.withCommandsClient(client -> {
        for (List<String> idPart : Iterables.partition(idSet, options.getMaxSaddSize())) {
          final String[] ids = idPart.toArray(new String[idPart.size()]);
          client.sadd(allOfTypeReindex(type), ids);
          saddOperations.incrementAndGet();
          client.sadd(allOfTypeId(type), ids);
          saddOperations.incrementAndGet();
        }

        for (int i = 0; i < keysToSet.size(); i = i + 2) {
          try {
            client.set(keysToSet.get(i), keysToSet.get(i+1));
            setOperations.incrementAndGet();
          } catch (JedisException|DynoException e) {
            log.error(type + " encountered Redis exception while setting cache data, marking item as failed", e);
            failedKeys.add(keysToSet.get(i));
          }
        }

        if (!relationshipNames.isEmpty()) {
          for (List<String> relNamesPart : Iterables.partition(relationshipNames, options.getMaxSaddSize())) {
            try {
              client.sadd(allRelationshipsId(type), relNamesPart.toArray(new String[relNamesPart.size()]));
              saddOperations.incrementAndGet();
            } catch (JedisException|DynoException e) {
              log.error(type + " encountered Redis exception while adding a relationship, marking " + relNamesPart.size() + " relationships as failed", e);
              failedKeys.add(allRelationshipsId(type));
            }
          }
        }

        if (!updatedHashes.isEmpty()) {
          // Prune all hashes that might be associated with failed cache writes
          if (!failedKeys.isEmpty()) {
            log.info(type + " failed writing caches for ~" + failedKeys.size() + " items, pruning their associated hashes");
            Set<String> invalidHashIds = updatedHashes.entrySet().stream()
              .filter(it -> failedKeys.contains(it.getKey()))
              .map(Entry::getKey)
              .collect(Collectors.toSet());

            invalidHashIds.forEach(it -> {
              updatedHashes.remove(it);
              client.del(hashKey(hashesId(type), it));
            });
          }

          for (Entry<String, String> hashEntry : updatedHashes.entrySet()) {
            client.setex(hashKey(hashesId(type), hashEntry.getKey()), HASH_EXPIRY_SECONDS, hashEntry.getValue());
            setOperations.incrementAndGet();
          }
        }

        for (Map.Entry<String, Integer> ttlEntry : ttlSecondsByKey.entrySet()) {
          client.expire(ttlEntry.getKey(), ttlEntry.getValue());
        }
        expireOperations.addAndGet(ttlSecondsByKey.size());
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
      setOperations.get(),
      msetOperations.get(),
      hmsetOperations.get(),
      0,
      expireOperations.get()
    );
  }

  @Override
  protected void evictItems(String type, List<String> identifiers, Collection<String> allRelationships) {
    List<String> delKeys = new ArrayList<>((allRelationships.size() + 1) * identifiers.size());
    for (String id : identifiers) {
      for (String rel : allRelationships) {
        delKeys.add(relationshipId(type, id, rel));
      }
      delKeys.add(attributesId(type, id));
    }

    AtomicInteger delOperations = new AtomicInteger();
    AtomicInteger hdelOperations = new AtomicInteger();
    AtomicInteger sremOperations = new AtomicInteger();

    redisClientDelegate.withCommandsClient(client -> {
      for (String key : delKeys) {
        client.del(key);
        delOperations.incrementAndGet();
        client.hdel(hashesId(type), key);
        hdelOperations.incrementAndGet();
      }

      for (List<String> idPartition : Lists.partition(identifiers, options.getMaxDelSize())) {
        String[] ids = idPartition.toArray(new String[idPartition.size()]);
        client.srem(allOfTypeId(type), ids);
        sremOperations.incrementAndGet();
        client.srem(allOfTypeReindex(type), ids);
        sremOperations.incrementAndGet();
      }
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

  @Override
  protected List<String> getHashValues(List<String> hashKeys, String hashesId) {
    final List<String> hashValues = new ArrayList<>(hashKeys.size());
    redisClientDelegate.withCommandsClient(c -> {
      // TODO rz - Dynomite mget perf is O(n^2), once fixed we can go to mget.
      for (String hashKey : hashKeys) {
        hashValues.add(c.get(hashKey(hashesId, hashKey)));
      }
    });
    return hashValues;
  }

  private String hashKey(String hashesId, String key) {
    return hashesId + ":" + key;
  }
}
