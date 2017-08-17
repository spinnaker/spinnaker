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
import com.google.common.collect.Lists;
import com.netflix.dyno.connectionpool.exception.DynoException;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.dynomite.DynomiteClientDelegate;
import com.netflix.spinnaker.cats.redis.cache.AbstractRedisCache;
import com.netflix.spinnaker.cats.redis.cache.RedisCacheOptions;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.exceptions.JedisException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DynomiteCache extends AbstractRedisCache {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private static final RetryPolicy redisRetryPolicy = new RetryPolicy()
    .retryOn(Arrays.asList(JedisException.class, DynoException.class))
    .withDelay(500, TimeUnit.MILLISECONDS)
    .withMaxRetries(3);

  public DynomiteCache(String prefix, DynomiteClientDelegate dynomiteClientDelegate, ObjectMapper objectMapper, RedisCacheOptions options, CacheMetrics cacheMetrics) {
    super(prefix, dynomiteClientDelegate, objectMapper, options, cacheMetrics);
  }

  @Override
  public void mergeItems(String type, Collection<CacheData> items) {
    if (items.isEmpty()){
      return;
    }

    AtomicInteger keysWritten = new AtomicInteger();
    AtomicInteger relationships = new AtomicInteger();
    AtomicInteger saddOperations = new AtomicInteger();
    AtomicInteger setOperations = new AtomicInteger();
    AtomicInteger expireOperations = new AtomicInteger();
    AtomicInteger delOperations = new AtomicInteger();
    AtomicInteger skippedWrites = new AtomicInteger();
    AtomicInteger hashesUpdated = new AtomicInteger();

    final Map<String, String> hashes = getHashes(type, items);
    redisClientDelegate.withCommandsClient(client -> {
      for (CacheData item : items) {
        MergeOp op = buildMergeOp(type, item, hashes);
        skippedWrites.addAndGet(op.skippedWrites);

        if (op.keysToSet.isEmpty()) {
          continue;
        }

        Failsafe
          .with(redisRetryPolicy)
          .onFailure(failure -> {
            log.error("Encountered repeated failures while setting " + type + " cache data for " + item.getId(), failure);
            op.hashesToSet.keySet().forEach(it -> {
              String hashKey = hashKey(hashesId(type), it);
              client.del(hashKey);
              delOperations.incrementAndGet();
            });
            throw new RuntimeException("Failed running caching agent", failure);
          })
          .run(() -> {
            for (int i = 0; i < op.keysToSet.size(); i = i + 2) {
              client.set(op.keysToSet.get(i), op.keysToSet.get(i + 1));
              setOperations.incrementAndGet();
              keysWritten.incrementAndGet();
            }

            if (!op.relNames.isEmpty()) {
              client.sadd(allRelationshipsId(type), op.relNames.toArray(new String[op.relNames.size()]));
              saddOperations.incrementAndGet();
              relationships.addAndGet(op.relNames.size());
            }

            if (item.getTtlSeconds() > 0) {
              for (String key : op.keysToSet) {
                client.expire(key, item.getTtlSeconds());
                expireOperations.incrementAndGet();
              }
            }

            client.sadd(allOfTypeReindex(type), item.getId());
            saddOperations.incrementAndGet();
            client.sadd(allOfTypeId(type), item.getId());
            saddOperations.incrementAndGet();

            if (!op.hashesToSet.isEmpty()) {
              for (Entry<String, String> hashEntry : op.hashesToSet.entrySet()) {
                client.setex(hashKey(hashesId(type), hashEntry.getKey()), getHashExpiry(), hashEntry.getValue());
                setOperations.incrementAndGet();
                hashesUpdated.incrementAndGet();
              }
            }
          });
      }
    });

    cacheMetrics.merge(
      prefix,
      type,
      items.size(),
      keysWritten.get(),
      relationships.get(),
      skippedWrites.get(),
      hashesUpdated.get(),
      saddOperations.get(),
      setOperations.get(),
      0,
      0,
      0,
      expireOperations.get(),
      delOperations.get()
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
    AtomicInteger sremOperations = new AtomicInteger();

    Failsafe
      .with(redisRetryPolicy)
      .run(() -> redisClientDelegate.withCommandsClient(client -> {
        for (List<String> idPartition : Lists.partition(identifiers, options.getMaxDelSize())) {
          String[] ids = idPartition.toArray(new String[idPartition.size()]);
          client.srem(allOfTypeId(type), ids);
          sremOperations.incrementAndGet();
          client.srem(allOfTypeReindex(type), ids);
          sremOperations.incrementAndGet();
        }

        for (String key : delKeys) {
          client.del(key);
          delOperations.incrementAndGet();
          client.del(hashKey(hashesId(type), key));
          delOperations.incrementAndGet();
        }
      }));

    cacheMetrics.evict(
      prefix,
      type,
      identifiers.size(),
      delKeys.size(),
      delKeys.size(),
      delOperations.get(),
      0,
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

  private int getHashExpiry() {
    // between 1 and 3 hours; boundary is exclusive
    return (int) Duration.ofMinutes(ThreadLocalRandom.current().nextInt(60, 4 * 60)).getSeconds();
  }
}
