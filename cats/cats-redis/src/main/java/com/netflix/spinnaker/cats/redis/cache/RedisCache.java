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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RedisCache extends AbstractRedisCache {

  public interface CacheMetrics {
    default void merge(
        String prefix,
        String type,
        int itemCount,
        int keysWritten,
        int relationshipCount,
        int hashMatches,
        int hashUpdates,
        int saddOperations,
        int msetOperations,
        int hmsetOperations,
        int pipelineOperations,
        int expireOperations) {
      // noop
    }

    default void evict(
        String prefix,
        String type,
        int itemCount,
        int keysDeleted,
        int hashesDeleted,
        int delOperations,
        int hdelOperations,
        int sremOperations) {
      // noop
    }

    default void get(
        String prefix,
        String type,
        int itemCount,
        int requestedSize,
        int keysRequested,
        int relationshipsRequested,
        int mgetOperations) {
      // noop
    }

    class NOOP implements CacheMetrics {}
  }

  private final CacheMetrics cacheMetrics;

  public RedisCache(
      String prefix,
      RedisClientDelegate redisClientDelegate,
      ObjectMapper objectMapper,
      RedisCacheOptions options,
      CacheMetrics cacheMetrics) {
    super(prefix, redisClientDelegate, objectMapper, options);
    this.cacheMetrics = cacheMetrics == null ? new CacheMetrics.NOOP() : cacheMetrics;
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
      redisClientDelegate.withMultiKeyPipeline(
          pipeline -> {
            for (List<String> idPart : Iterables.partition(idSet, options.getMaxSaddSize())) {
              final String[] ids = idPart.toArray(new String[idPart.size()]);
              pipeline.sadd(allOfTypeId(type), ids);
              saddOperations.incrementAndGet();
            }

            for (List<String> keys : Lists.partition(keysToSet, options.getMaxMsetSize())) {
              pipeline.mset(keys.toArray(new String[keys.size()]));
              msetOperations.incrementAndGet();
            }

            if (!relationshipNames.isEmpty()) {
              for (List<String> relNamesPart :
                  Iterables.partition(relationshipNames, options.getMaxSaddSize())) {
                pipeline.sadd(
                    allRelationshipsId(type),
                    relNamesPart.toArray(new String[relNamesPart.size()]));
                saddOperations.incrementAndGet();
              }
            }

            if (!updatedHashes.isEmpty()) {
              for (List<String> hashPart :
                  Iterables.partition(updatedHashes.keySet(), options.getMaxHmsetSize())) {
                pipeline.hmset(
                    hashesId(type),
                    updatedHashes.subMap(
                        hashPart.get(0), true, hashPart.get(hashPart.size() - 1), true));
                hmsetOperations.incrementAndGet();
              }
            }
            pipeline.sync();
            pipelineOperations.incrementAndGet();
          });

      redisClientDelegate.withMultiKeyPipeline(
          pipeline -> {
            for (List<Map.Entry<String, Integer>> ttlPart :
                Iterables.partition(ttlSecondsByKey.entrySet(), options.getMaxPipelineSize())) {
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
        msetOperations.get(),
        hmsetOperations.get(),
        pipelineOperations.get(),
        expireOperations.get());
  }

  @Override
  protected void evictItems(
      String type, List<String> identifiers, Collection<String> allRelationships) {
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
    redisClientDelegate.withMultiKeyPipeline(
        pipeline -> {
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
        sremOperations.get());
  }

  @Override
  protected Collection<CacheData> getItems(String type, List<String> ids, List<String> knownRels) {
    final int singleResultSize = knownRels.size() + 1;

    final List<String> keysToGet = new ArrayList<>(singleResultSize * ids.size());
    for (String id : ids) {
      keysToGet.add(attributesId(type, id));
      for (String rel : knownRels) {
        keysToGet.add(relationshipId(type, id, rel));
      }
    }

    final List<String> keyResult = new ArrayList<>(keysToGet.size());

    int mgetOperations =
        redisClientDelegate.withMultiClient(
            c -> {
              int ops = 0;
              for (List<String> part : Lists.partition(keysToGet, options.getMaxMgetSize())) {
                ops++;
                keyResult.addAll(c.mget(part.toArray(new String[part.size()])));
              }
              return ops;
            });

    if (keyResult.size() != keysToGet.size()) {
      throw new RuntimeException("Expected same size result as request");
    }

    Collection<CacheData> results = new ArrayList<>(ids.size());
    Iterator<String> idIterator = ids.iterator();
    for (int ofs = 0; ofs < keyResult.size(); ofs += singleResultSize) {
      CacheData item =
          extractItem(idIterator.next(), keyResult.subList(ofs, ofs + singleResultSize), knownRels);
      if (item != null) {
        results.add(item);
      }
    }

    cacheMetrics.get(
        prefix,
        type,
        results.size(),
        ids.size(),
        keysToGet.size(),
        knownRels.size(),
        mgetOperations);
    return results;
  }

  private CacheData extractItem(String id, List<String> keyResult, List<String> knownRels) {
    if (keyResult.get(0) == null) {
      return null;
    }

    try {
      final Map<String, Object> attributes = objectMapper.readValue(keyResult.get(0), ATTRIBUTES);
      final Map<String, Collection<String>> relationships = new HashMap<>(keyResult.size() - 1);
      for (int relIdx = 1; relIdx < keyResult.size(); relIdx++) {
        String rel = keyResult.get(relIdx);
        if (rel != null) {
          String relType = knownRels.get(relIdx - 1);
          Collection<String> deserializedRel =
              objectMapper.readValue(rel, getRelationshipsTypeReference());
          relationships.put(relType, deserializedRel);
        }
      }

      return new DefaultCacheData(id, attributes, relationships);

    } catch (IOException deserializationException) {
      throw new RuntimeException("Deserialization failed", deserializationException);
    }
  }

  private static class MergeOp {
    public final Set<String> relNames;
    public final List<String> keysToSet;
    public final Map<String, String> hashesToSet;
    public final int skippedWrites;

    MergeOp(
        Set<String> relNames,
        List<String> keysToSet,
        Map<String, String> hashesToSet,
        int skippedWrites) {
      this.relNames = relNames;
      this.keysToSet = keysToSet;
      this.hashesToSet = hashesToSet;
      this.skippedWrites = skippedWrites;
    }
  }

  private MergeOp buildMergeOp(String type, CacheData cacheData, Map<String, String> hashes) {
    int skippedWrites = 0;
    final String serializedAttributes;
    boolean hasTtl = cacheData.getTtlSeconds() > 0;
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
    final List<String> keysToSet = new ArrayList<>((cacheData.getRelationships().size() + 1) * 2);
    if (serializedAttributes != null
        && hashCheck(
            hashes,
            attributesId(type, cacheData.getId()),
            serializedAttributes,
            keysToSet,
            hashesToSet,
            hasTtl)) {
      skippedWrites++;
    }

    if (!cacheData.getRelationships().isEmpty()) {
      for (Map.Entry<String, Collection<String>> relationship :
          cacheData.getRelationships().entrySet()) {
        final String relationshipValue;
        try {
          relationshipValue =
              objectMapper.writeValueAsString(new LinkedHashSet<>(relationship.getValue()));
        } catch (JsonProcessingException serializationException) {
          throw new RuntimeException("Relationship serialization failed", serializationException);
        }
        if (hashCheck(
            hashes,
            relationshipId(type, cacheData.getId(), relationship.getKey()),
            relationshipValue,
            keysToSet,
            hashesToSet,
            hasTtl)) {
          skippedWrites++;
        }
      }
    }

    return new MergeOp(
        cacheData.getRelationships().keySet(), keysToSet, hashesToSet, skippedWrites);
  }

  private List<String> getKeys(String type, Collection<CacheData> cacheDatas) {
    final Collection<String> keys = new HashSet<>();
    for (CacheData cacheData : cacheDatas) {
      if (!cacheData.getAttributes().isEmpty()) {
        keys.add(attributesId(type, cacheData.getId()));
      }
      if (!cacheData.getRelationships().isEmpty()) {
        for (String relationship : cacheData.getRelationships().keySet()) {
          keys.add(relationshipId(type, cacheData.getId(), relationship));
        }
      }
    }
    return new ArrayList<>(keys);
  }

  private List<String> getHashValues(List<String> hashKeys, String hashesId) {
    final List<String> hashValues = new ArrayList<>(hashKeys.size());
    redisClientDelegate.withCommandsClient(
        c -> {
          for (List<String> hashPart : Lists.partition(hashKeys, options.getMaxHmgetSize())) {
            hashValues.addAll(
                c.hmget(
                    hashesId, Arrays.copyOf(hashPart.toArray(), hashPart.size(), String[].class)));
          }
        });
    return hashValues;
  }

  /**
   * Compares the hash of serializedValue against an existing hash, if they do not match adds
   * serializedValue to keys and the new hash to updatedHashes.
   *
   * @param hashes the existing hash values
   * @param id the id of the item
   * @param serializedValue the serialized value
   * @param keys values to persist - if the hash does not match id and serializedValue are appended
   * @param updatedHashes hashes to persist - if the hash does not match adds an entry of id ->
   *     computed hash
   * @param hasTtl if the key has a ttl - generally this means the key should not be hashed due to
   *     consistency issues between the hash key, and the key itself
   * @return true if the hash matched, false otherwise
   */
  private boolean hashCheck(
      Map<String, String> hashes,
      String id,
      String serializedValue,
      List<String> keys,
      Map<String, String> updatedHashes,
      boolean hasTtl) {
    if (options.isHashingEnabled() && !hasTtl) {
      final String hash =
          Hashing.sha1().newHasher().putString(serializedValue, UTF_8).hash().toString();
      final String existingHash = hashes.get(id);
      if (hash.equals(existingHash)) {
        return true;
      }
      updatedHashes.put(id, hash);
    }

    keys.add(id);
    keys.add(serializedValue);
    return false;
  }

  private Map<String, String> getHashes(String type, Collection<CacheData> items) {
    if (isHashingDisabled(type)) {
      return Collections.emptyMap();
    }

    final List<String> hashKeys = getKeys(type, items);
    if (hashKeys.isEmpty()) {
      return Collections.emptyMap();
    }

    final List<String> hashValues = getHashValues(hashKeys, hashesId(type));
    if (hashValues.size() != hashKeys.size()) {
      throw new RuntimeException("Expected same size result as request");
    }

    final Map<String, String> hashes = new HashMap<>(hashKeys.size());
    for (int i = 0; i < hashValues.size(); i++) {
      final String hashValue = hashValues.get(i);
      if (hashValue != null) {
        hashes.put(hashKeys.get(i), hashValue);
      }
    }

    return hashes;
  }

  private String hashesId(String type) {
    return String.format("%s:%s:hashes", prefix, type);
  }
}
