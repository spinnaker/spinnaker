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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.netflix.dyno.connectionpool.exception.DynoException;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.dynomite.DynomiteClientDelegate;
import com.netflix.spinnaker.cats.redis.cache.AbstractRedisCache;
import com.netflix.spinnaker.cats.redis.cache.RedisCacheOptions;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.exceptions.JedisException;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;

public class DynomiteCache extends AbstractRedisCache {

  public interface CacheMetrics {
    default void merge(String prefix,
                       String type,
                       int itemCount,
                       int relationshipCount,
                       int hashMatches,
                       int hashUpdates,
                       int saddOperations,
                       int hmsetOperations,
                       int expireOperations,
                       int delOperations) {
      // noop
    }

    default void evict(String prefix,
                       String type,
                       int itemCount,
                       int delOperations,
                       int sremOperations) {
      // noop
    }

    default void get(String prefix,
                     String type,
                     int itemCount,
                     int requestedSize,
                     int relationshipsRequested,
                     int hmgetAllOperations) {
      // noop
    }

    class NOOP implements CacheMetrics {}
  }

  private final Logger log = LoggerFactory.getLogger(getClass());

  private static final RetryPolicy redisRetryPolicy = new RetryPolicy()
    .retryOn(Arrays.asList(JedisException.class, DynoException.class))
    .withDelay(500, TimeUnit.MILLISECONDS)
    .withMaxRetries(3);

  private final CacheMetrics cacheMetrics;

  public DynomiteCache(String prefix, DynomiteClientDelegate dynomiteClientDelegate, ObjectMapper objectMapper, RedisCacheOptions options, CacheMetrics cacheMetrics) {
    super(prefix, dynomiteClientDelegate, objectMapper, options);
    this.cacheMetrics = cacheMetrics == null ? new CacheMetrics.NOOP() : cacheMetrics;
  }

  @Override
  public void mergeItems(String type, Collection<CacheData> items) {
    if (items.isEmpty()){
      return;
    }

    AtomicInteger relationships = new AtomicInteger();
    AtomicInteger hmsetOperations = new AtomicInteger();
    AtomicInteger saddOperations = new AtomicInteger();
    AtomicInteger expireOperations = new AtomicInteger();
    AtomicInteger delOperations = new AtomicInteger();
    AtomicInteger skippedWrites = new AtomicInteger();
    AtomicInteger hashesUpdated = new AtomicInteger();

    redisClientDelegate.withCommandsClient(client -> {
      for (CacheData item : items) {
        MergeOp op = buildHashedMergeOp(type, item);
        skippedWrites.addAndGet(op.skippedWrites);

        if (op.valuesToSet.isEmpty()) {
          continue;
        }

        Failsafe
          .with(redisRetryPolicy)
          .onFailure(failure -> {
            log.error("Encountered repeated failures while setting " + type + " cache data for " + item.getId(), failure);
            client.del(itemHashesId(type, item.getId()));
            delOperations.incrementAndGet();
            throw new RuntimeException("Failed running caching agent", failure);
          })
          .run(() -> {
            client.hmset(itemId(type, item.getId()), op.valuesToSet);
            hmsetOperations.incrementAndGet();

            if (!op.relNames.isEmpty()) {
              client.sadd(allRelationshipsId(type), op.relNames.toArray(new String[op.relNames.size()]));
              saddOperations.incrementAndGet();
              relationships.addAndGet(op.relNames.size());
            }

            if (item.getTtlSeconds() > 0) {
              client.expire(itemId(type, item.getId()), item.getTtlSeconds());
              expireOperations.incrementAndGet();
            }

            client.sadd(allOfTypeReindex(type), item.getId());
            saddOperations.incrementAndGet();
            client.sadd(allOfTypeId(type), item.getId());
            saddOperations.incrementAndGet();

            if (!op.hashesToSet.isEmpty()) {
              client.hmset(itemHashesId(type, item.getId()), op.hashesToSet);
              hmsetOperations.incrementAndGet();
              client.expire(itemHashesId(type, item.getId()), getHashExpiry());
              expireOperations.incrementAndGet();
              hashesUpdated.addAndGet(op.hashesToSet.size());
            }
          });
      }
    });

    cacheMetrics.merge(
      prefix,
      type,
      items.size(),
      relationships.get(),
      skippedWrites.get(),
      hashesUpdated.get(),
      saddOperations.get(),
      hmsetOperations.get(),
      expireOperations.get(),
      delOperations.get()
    );
  }

  @Override
  protected void evictItems(String type, List<String> identifiers, Collection<String> allRelationships) {
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

        for (String id : identifiers) {
          client.del(itemId(type, id));
          delOperations.incrementAndGet();
          client.del(itemHashesId(type, id));
          delOperations.incrementAndGet();
        }
      }));

    cacheMetrics.evict(
      prefix,
      type,
      identifiers.size(),
      delOperations.get(),
      sremOperations.get()
    );
  }

  @Override
  protected Collection<CacheData> getItems(String type, List<String> ids, List<String> knownRels) {
    Map<String, Map<String, String>> rawItems = new HashMap<>();
    int hmgetAllOperations = redisClientDelegate.withCommandsClient(c -> {
      int ops = 0;
      for (String id : ids) {
        Map<String, String> item = c.hgetAll(itemId(type, id));
        if (item != null && !item.isEmpty()) {
          rawItems.put(id, item);
          ops++;
        }
      }
      return ops;
    });

    Collection<CacheData> results = new ArrayList<>(ids.size());
    for (Map.Entry<String, Map<String, String>> rawItem : rawItems.entrySet()) {
      CacheData item = extractHashedItem(rawItem.getKey(), rawItem.getValue(), knownRels);
      if (item != null) {
        results.add(item);
      }
    }

    cacheMetrics.get(prefix, type, results.size(), ids.size(), knownRels.size(), hmgetAllOperations);
    return results;
  }

  private CacheData extractHashedItem(String id, Map<String, String> values, List<String> knownRels) {
    if (values == null) {
      return null;
    }

    try {
      final Map<String, Object> attributes;
      if (values.get("attributes") != null) {
        attributes = objectMapper.readValue(values.get("attributes"), ATTRIBUTES);
      } else {
        attributes = null;
      }
      final Map<String, Collection<String>> relationships = new HashMap<>();
      for (Map.Entry<String, String> value : values.entrySet()) {
        // TODO rz - Get relationships individually? Is this actually an optimization?
        if (value.getKey().equals("attributes") || value.getKey().equals("id") || !knownRels.contains(value.getKey())) {
          continue;
        }
        Collection<String> deserializedRel = objectMapper.readValue(value.getValue(), RELATIONSHIPS);
        relationships.put(value.getKey(), deserializedRel);
      }
      return new DefaultCacheData(id, attributes, relationships);
    } catch (IOException deserializationException) {
      throw new RuntimeException("Deserialization failed", deserializationException);
    }
  }

  private static class MergeOp {
    final Set<String> relNames;
    final Map<String, String> valuesToSet;
    final Map<String, String> hashesToSet;
    final int skippedWrites;

    public MergeOp(Set<String> relNames, Map<String, String> valuesToSet, Map<String, String> hashesToSet, int skippedWrites) {
      this.relNames = relNames;
      this.valuesToSet = valuesToSet;
      this.hashesToSet = hashesToSet;
      this.skippedWrites = skippedWrites;
    }
  }

  private boolean hashCheck(Map<String, String> hashes, String id, String serializedValue, Map<String, String> updatedHashes) {
    if (options.isHashingEnabled()) {
      final String hash = Hashing.sha1().newHasher().putString(serializedValue, UTF_8).hash().toString();
      final String existingHash = hashes.get(id);
      if (hash.equals(existingHash)) {
        return true;
      }
      updatedHashes.put(id, hash);
    }
    return false;
  }

  private MergeOp buildHashedMergeOp(String type, CacheData cacheData) {
    int skippedWrites = 0;
    final String serializedAttributes;
    try {
      if (cacheData.getAttributes().isEmpty()) {
        serializedAttributes = null;
      } else {
        serializedAttributes = objectMapper.writeValueAsString(cacheData.getAttributes());
      }
    } catch (JsonProcessingException serializationException) {
      throw new RuntimeException("Attribute serialization failed", serializationException);
    }

    final Map<String, String> hashes = getHashes(type, cacheData);

    final Map<String, String> hashesToSet = new HashMap<>();
    final Map<String, String> valuesToSet = new HashMap<>();
    if (serializedAttributes != null && hashCheck(hashes, attributesId(type, cacheData.getId()), serializedAttributes, hashesToSet)) {
      skippedWrites++;
    } else if (serializedAttributes != null) {
      valuesToSet.put("attributes", serializedAttributes);
    }

    if (!cacheData.getRelationships().isEmpty()) {
      for (Map.Entry<String, Collection<String>> relationship : cacheData.getRelationships().entrySet()) {
        final String relationshipValue;
        try {
          relationshipValue = objectMapper.writeValueAsString(new LinkedHashSet<>(relationship.getValue()));
        } catch (JsonProcessingException serializationException) {
          throw new RuntimeException("Relationship serialization failed", serializationException);
        }
        if (hashCheck(hashes, relationshipId(type, cacheData.getId(), relationship.getKey()), relationshipValue, hashesToSet)) {
          skippedWrites++;
        } else {
          valuesToSet.put(relationship.getKey(), relationshipValue);
        }
      }
    }

    return new MergeOp(cacheData.getRelationships().keySet(), valuesToSet, hashesToSet, skippedWrites);
  }

  private Map<String, String> getHashes(String type, CacheData item) {
    if (isHashingDisabled(type)) {
      return Collections.emptyMap();
    }

    return redisClientDelegate.withCommandsClient(c -> {
      return c.hgetAll(itemHashesId(type, item.getId()));
    });
  }

  private int getHashExpiry() {
    // between 1 and 3 hours; boundary is exclusive
    return (int) Duration.ofMinutes(ThreadLocalRandom.current().nextInt(60, 4 * 60)).getSeconds();
  }

  private String itemId(String type, String id) {
    return String.format("%s:%s:item:%s", prefix, type, id);
  }

  private String itemHashesId(String type, String id) {
    return String.format("%s:%s:hashes:%s", prefix, type, id);
  }
}
