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
import com.netflix.dyno.jedis.DynoJedisPipeline;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.compression.CompressionStrategy;
import com.netflix.spinnaker.cats.compression.NoopCompression;
import com.netflix.spinnaker.cats.dynomite.DynomiteUtils;
import com.netflix.spinnaker.cats.dynomite.ExcessiveDynoFailureRetries;
import com.netflix.spinnaker.cats.redis.cache.AbstractRedisCache;
import com.netflix.spinnaker.cats.redis.cache.RedisCacheOptions;
import com.netflix.spinnaker.kork.dynomite.DynomiteClientDelegate;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Response;
import redis.clients.jedis.exceptions.JedisException;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.lang.String.format;
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

  // TODO rz - Make retry policy configurable
  private static final RetryPolicy REDIS_RETRY_POLICY = DynomiteUtils.greedyRetryPolicy(500);

  private final CacheMetrics cacheMetrics;

  private final CompressionStrategy compressionStrategy;

  public DynomiteCache(String prefix,
                       DynomiteClientDelegate dynomiteClientDelegate,
                       ObjectMapper objectMapper,
                       RedisCacheOptions options,
                       CacheMetrics cacheMetrics,
                       CompressionStrategy compressionStrategy) {
    super(prefix, dynomiteClientDelegate, objectMapper, options);
    this.cacheMetrics = cacheMetrics == null ? new CacheMetrics.NOOP() : cacheMetrics;
    this.compressionStrategy = compressionStrategy == null ? new NoopCompression() : compressionStrategy;
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

    Map<CacheData, Map<String, String>> allHashes = getAllHashes(type, items);
    Failsafe
      .with(REDIS_RETRY_POLICY)
      .onRetriesExceeded(failure -> {
        log.error("Encountered repeated failures while caching {}:{}, attempting cleanup", prefix, type, failure);
        try {
          redisClientDelegate.withPipeline(pipeline -> {
            DynoJedisPipeline p = (DynoJedisPipeline) pipeline;
            for (CacheData item : items) {
              p.del(itemHashesId(type, item.getId()));
              delOperations.incrementAndGet();
            }
            p.sync();
          });
        } catch (JedisException|DynoException e) {
          log.error("Failed cleaning up hashes in failure handler in {}:{}", prefix, type, e);
        }
        throw new ExcessiveDynoFailureRetries(format("Running cache agent %s:%s", prefix, type), failure);
      })
      .run(() -> redisClientDelegate.withPipeline(pipeline -> {
        DynoJedisPipeline p = (DynoJedisPipeline) pipeline;

        // https://github.com/xetorthio/jedis/issues/758
        boolean pipelineHasOps = false;
        for (CacheData item : items) {
          MergeOp op = buildHashedMergeOp(type, item, allHashes.get(item));
          skippedWrites.addAndGet(op.skippedWrites);

          if (op.valuesToSet.isEmpty()) {
            continue;
          }

          pipelineHasOps = true;

          p.hmset(itemId(type, item.getId()), op.valuesToSet);
          hmsetOperations.incrementAndGet();

          if (!op.relNames.isEmpty()) {
            p.sadd(allRelationshipsId(type), op.relNames.toArray(new String[op.relNames.size()]));
            saddOperations.incrementAndGet();
            relationships.addAndGet(op.relNames.size());
          }

          if (item.getTtlSeconds() > 0) {
            p.expire(itemId(type, item.getId()), item.getTtlSeconds());
            expireOperations.incrementAndGet();
          }

          p.sadd(allOfTypeId(type), item.getId());
          saddOperations.incrementAndGet();

          if (!op.hashesToSet.isEmpty()) {
            p.hmset(itemHashesId(type, item.getId()), op.hashesToSet);
            hmsetOperations.incrementAndGet();
            p.expire(itemHashesId(type, item.getId()), getHashExpiry());
            expireOperations.incrementAndGet();
            hashesUpdated.addAndGet(op.hashesToSet.size());
          }
        }
        if (pipelineHasOps) {
          p.sync();
        }
      }));

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
      .with(REDIS_RETRY_POLICY)
      .onRetriesExceeded(failure -> {
        throw new ExcessiveDynoFailureRetries(format("Evicting items for %s:%s", prefix, type), failure);
      })
      .run(() -> redisClientDelegate.withPipeline(pipeline -> {
        DynoJedisPipeline p = (DynoJedisPipeline) pipeline;

        for (List<String> idPartition : Lists.partition(identifiers, options.getMaxDelSize())) {
          String[] ids = idPartition.toArray(new String[idPartition.size()]);
          pipeline.srem(allOfTypeId(type), ids);
          sremOperations.incrementAndGet();
        }

        for (String id : identifiers) {
          pipeline.del(itemId(type, id));
          delOperations.incrementAndGet();
          pipeline.del(itemHashesId(type, id));
          delOperations.incrementAndGet();
        }

        if (!identifiers.isEmpty()) {
          p.sync();
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
    if (ids.isEmpty()) {
      return new ArrayList<>();
    }

    AtomicInteger hmgetAllOperations = new AtomicInteger();
    Map<String, Map<String, String>> rawItems = Failsafe
      .with(REDIS_RETRY_POLICY)
      .onRetriesExceeded(failure -> {
        throw new ExcessiveDynoFailureRetries(format("Getting items for %s:%s", prefix, type), failure);
      })
      .get(() -> redisClientDelegate.withPipeline(pipeline -> {
        DynoJedisPipeline p = (DynoJedisPipeline) pipeline;

        Map<String, Response<Map<String, String>>> responses = new HashMap<>();
        for (String id : ids) {
          responses.put(id, pipeline.hgetAll(itemId(type, id)));
          hmgetAllOperations.incrementAndGet();
        }
        p.sync();

        return responses.entrySet().stream()
          .filter(e -> !e.getValue().get().isEmpty())
          .collect(Collectors.toMap(Entry::getKey, it -> it.getValue().get()));
      }));

    Collection<CacheData> results = new ArrayList<>(ids.size());
    for (Map.Entry<String, Map<String, String>> rawItem : rawItems.entrySet()) {
      CacheData item = extractHashedItem(type, rawItem.getKey(), rawItem.getValue(), knownRels);
      if (item != null) {
        results.add(item);
      }
    }

    cacheMetrics.get(prefix, type, results.size(), ids.size(), knownRels.size(), hmgetAllOperations.get());
    return results;
  }

  private CacheData extractHashedItem(String type, String id, Map<String, String> values, List<String> knownRels) {
    if (values == null) {
      return null;
    }

    try {
      final Map<String, Object> attributes;
      if (values.get("attributes") != null) {
        attributes = objectMapper.readValue(compressionStrategy.decompress(values.get("attributes")), ATTRIBUTES);
      } else {
        attributes = null;
      }
      final Map<String, Collection<String>> relationships = new HashMap<>();
      for (Map.Entry<String, String> value : values.entrySet()) {
        if (value.getKey().equals("attributes") || value.getKey().equals("id") || !knownRels.contains(value.getKey())) {
          continue;
        }

        Collection<String> deserializedRel;
        try {
          deserializedRel = objectMapper.readValue(
            compressionStrategy.decompress(value.getValue()),
            getRelationshipsTypeReference()
          );
        } catch (JsonProcessingException e) {
          log.warn("Failed processing property '{}' on item '{}'", value.getKey(), itemId(type, id));
          continue;
        }
        relationships.put(value.getKey(), deserializedRel);
      }
      return new DefaultCacheData(id, attributes, relationships);
    } catch (IOException deserializationException) {
      throw new RuntimeException("Deserialization failed", deserializationException);
    }
  }

  @Override
  protected Set<String> scanMembers(String setKey, Optional<String> glob) {
    return Failsafe
      .with(REDIS_RETRY_POLICY)
      .get(() -> super.scanMembers(setKey, glob));
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

  private boolean hashCheck(Map<String, String> hashes, String id, String serializedValue, Map<String, String> updatedHashes, boolean hasTtl) {
    if (options.isHashingEnabled() && !hasTtl) {
      final String hash = Hashing.sha1().newHasher().putString(serializedValue, UTF_8).hash().toString();
      final String existingHash = hashes.get(id);
      if (hash.equals(existingHash)) {
        return true;
      }
      updatedHashes.put(id, hash);
    }
    return false;
  }

  private MergeOp buildHashedMergeOp(String type, CacheData cacheData, Map<String, String> hashes) {
    int skippedWrites = 0;
    final boolean hasTtl = cacheData.getTtlSeconds() > 0;
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

    final Map<String, String> hashesToSet = new HashMap<>();
    final Map<String, String> valuesToSet = new HashMap<>();
    if (serializedAttributes != null && hashCheck(hashes, attributesId(type, cacheData.getId()), serializedAttributes, hashesToSet, hasTtl)) {
      skippedWrites++;
    } else if (serializedAttributes != null) {
      valuesToSet.put("attributes", compressionStrategy.compress(serializedAttributes));
    }

    if (!cacheData.getRelationships().isEmpty()) {
      for (Map.Entry<String, Collection<String>> relationship : cacheData.getRelationships().entrySet()) {
        final String relationshipValue;
        try {
          relationshipValue = objectMapper.writeValueAsString(new LinkedHashSet<>(relationship.getValue()));
        } catch (JsonProcessingException serializationException) {
          throw new RuntimeException("Relationship serialization failed", serializationException);
        }
        if (hashCheck(hashes, relationshipId(type, cacheData.getId(), relationship.getKey()), relationshipValue, hashesToSet, hasTtl)) {
          skippedWrites++;
        } else {
          valuesToSet.put(relationship.getKey(), compressionStrategy.compress(relationshipValue));
        }
      }
    }

    return new MergeOp(cacheData.getRelationships().keySet(), valuesToSet, hashesToSet, skippedWrites);
  }

  private Map<CacheData, Map<String, String>> getAllHashes(String type, Collection<CacheData> items) {
    if (isHashingDisabled(type)) {
      return new HashMap<>();
    }

    return Failsafe
      .with(REDIS_RETRY_POLICY)
      .onRetriesExceeded(failure -> {
        throw new ExcessiveDynoFailureRetries(format("Getting all requested hashes for %s:%s", prefix, type), failure);
      })
      .get(() -> redisClientDelegate.withPipeline(pipeline -> {
        DynoJedisPipeline p = (DynoJedisPipeline) pipeline;

        Map<CacheData, Response<Map<String, String>>> responses = new HashMap<>();
        for (CacheData item : items) {
          responses.put(item, p.hgetAll(itemHashesId(type, item.getId())));
        }
        p.sync();

        return responses.entrySet().stream().collect(Collectors.toMap(Entry::getKey, it -> it.getValue().get()));
      }));
  }

  @Override
  protected boolean isHashingDisabled(String type) {
    return Failsafe
      .with(REDIS_RETRY_POLICY)
      .onRetriesExceeded(failure -> {
        throw new ExcessiveDynoFailureRetries(format("Getting hashing flag for %s:%s", prefix, type), failure);
      })
      .get(() -> super.isHashingDisabled(type));
  }

  private int getHashExpiry() {
    // between 1 and 3 hours; boundary is exclusive
    return (int) Duration.ofMinutes(ThreadLocalRandom.current().nextInt(60, 4 * 60)).getSeconds();
  }

  private String itemId(String type, String id) {
    return format("{%s:%s}:%s", prefix, type, id);
  }

  private String itemHashesId(String type, String id) {
    return format("{%s:%s}:hashes:%s", prefix, type, id);
  }

  @Override
  protected String attributesId(String type, String id) {
    return format("{%s:%s}:attributes:%s", prefix, type, id);
  }

  @Override
  protected String relationshipId(String type, String id, String relationship) {
    return format("{%s:%s}:relationships:%s:%s", prefix, type, id, relationship);
  }

  @Override
  protected String allRelationshipsId(String type) {
    return format("{%s:%s}:relationships", prefix, type);
  }

  @Override
  protected String allOfTypeId(String type) {
    return format("{%s:%s}:members", prefix, type);
  }
}
