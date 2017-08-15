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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.CacheFilter;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.cache.WriteableCache;
import com.netflix.spinnaker.cats.redis.RedisClientDelegate;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;

public abstract class AbstractRedisCache implements WriteableCache {

  public interface CacheMetrics {
    default void merge(String prefix,
                       String type,
                       int itemCount,
                       int keysWritten,
                       int relationshipCount,
                       int hashMatches,
                       int hashUpdates,
                       int saddOperations,
                       int setOperations,
                       int msetOperations,
                       int hmsetOperations,
                       int pipelineOperations,
                       int expireOperations,
                       int delOperations) {
      //noop
    }

    default void evict(String prefix,
                       String type,
                       int itemCount,
                       int keysDeleted,
                       int hashesDeleted,
                       int delOperations,
                       int hdelOperations,
                       int sremOperations) {
      //noop
    }

    default void get(String prefix,
                     String type,
                     int itemCount,
                     int requestedSize,
                     int keysRequested,
                     int relationshipsRequested,
                     int mgetOperations) {
      //noop
    }

    class NOOP implements CacheMetrics {
    }
  }

  private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {
  };
  private static final TypeReference<List<String>> RELATIONSHIPS = new TypeReference<List<String>>() {
  };

  protected final String prefix;
  protected final RedisClientDelegate redisClientDelegate;
  protected final ObjectMapper objectMapper;
  protected final RedisCacheOptions options;
  protected final CacheMetrics cacheMetrics;

  public AbstractRedisCache(String prefix, RedisClientDelegate redisClientDelegate, ObjectMapper objectMapper, RedisCacheOptions options, CacheMetrics cacheMetrics) {
    this.prefix = prefix;
    this.redisClientDelegate = redisClientDelegate;
    this.objectMapper = objectMapper.disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
    this.options = options;
    this.cacheMetrics = cacheMetrics == null ? new CacheMetrics.NOOP() : cacheMetrics;
  }

  abstract protected void mergeItems(String type, Collection<CacheData> items);

  abstract protected void evictItems(String type, List<String> identifiers, Collection<String> allRelationships);

  @Override
  public void merge(String type, CacheData item) {
    mergeAll(type, Collections.singletonList(item));
  }

  @Override
  public void mergeAll(String type, Collection<CacheData> items) {
    for (List<CacheData> partition : Iterables.partition(items, options.getMaxMergeBatchSize())) {
      mergeItems(type, partition);
    }
  }

  @Override
  public void evict(String type, String id) {
    evictAll(type, Collections.singletonList(id));
  }

  @Override
  public void evictAll(String type, Collection<String> identifiers) {
    if (identifiers.isEmpty()) {
      return;
    }
    final Collection<String> allRelationships = scanMembers(allRelationshipsId(type));
    for (List<String> items : Iterables.partition(new HashSet<>(identifiers), options.getMaxEvictBatchSize())) {
      evictItems(type, items, allRelationships);
    }
  }

  @Override
  public CacheData get(String type, String id) {
    return get(type, id, null);
  }

  @Override
  public CacheData get(String type, String id, CacheFilter cacheFilter) {
    Collection<CacheData> result = getAll(type, Arrays.asList(id), cacheFilter);
    if (result.isEmpty()) {
      return null;
    }
    return result.iterator().next();
  }

  @Override
  public Collection<CacheData> getAll(String type) {
    return getAll(type, (CacheFilter) null);
  }

  @Override
  public Collection<CacheData> getAll(String type, CacheFilter cacheFilter) {
    final Set<String> allIds = scanMembers(allOfTypeId(type));
    return getAll(type, allIds, cacheFilter);
  }

  @Override
  public Collection<CacheData> getAll(String type, String... identifiers) {
    return getAll(type, Arrays.asList(identifiers));
  }

  @Override
  public Collection<CacheData> getAll(String type, Collection<String> identifiers) {
    return getAll(type, identifiers, null);
  }

  @Override
  public Collection<CacheData> getAll(String type,
                                      Collection<String> identifiers,
                                      CacheFilter cacheFilter) {
    if (identifiers.isEmpty()) {
      return Collections.emptySet();
    }
    Collection<String> ids = new LinkedHashSet<>(identifiers);
    final List<String> knownRels;
    Set<String> allRelationships = scanMembers(allRelationshipsId(type));
    if (cacheFilter == null) {
      knownRels = new ArrayList<>(allRelationships);
    } else {
      knownRels = new ArrayList<>(cacheFilter.filter(CacheFilter.Type.RELATIONSHIP, allRelationships));
    }

    Collection<CacheData> result = new ArrayList<>(ids.size());

    for (List<String> idPart : Iterables.partition(ids, options.getMaxGetBatchSize())) {
      result.addAll(getItems(type, idPart, knownRels));
    }

    return result;
  }

  @Override
  public Collection<String> getIdentifiers(String type) {
    return scanMembers(allOfTypeId(type));
  }

  @Override
  public Collection<String> filterIdentifiers(String type, String glob) {
    return scanMembers(allOfTypeId(type), Optional.of(glob));
  }

  private Set<String> scanMembers(String setKey) {
    return scanMembers(setKey, Optional.empty());
  }

  private Set<String> scanMembers(String setKey, Optional<String> glob) {
    return redisClientDelegate.withCommandsClient(client -> {
      final Set<String> matches = new HashSet<>();
      final ScanParams scanParams = new ScanParams().count(options.getScanSize());
      glob.ifPresent(scanParams::match);
      String cursor = "0";
      while (true) {
        final ScanResult<String> scanResult = client.sscan(setKey, cursor, scanParams);
        matches.addAll(scanResult.getResult());
        cursor = scanResult.getStringCursor();
        if ("0".equals(cursor)) {
          return matches;
        }
      }
    });
  }

  protected static class MergeOp {
    public final Set<String> relNames;
    public final List<String> keysToSet;
    public final Map<String, String> hashesToSet;
    public final int skippedWrites;

    public MergeOp(Set<String> relNames, List<String> keysToSet, Map<String, String> hashesToSet, int skippedWrites) {
      this.relNames = relNames;
      this.keysToSet = keysToSet;
      this.hashesToSet = hashesToSet;
      this.skippedWrites = skippedWrites;
    }
  }

  /**
   * Compares the hash of serializedValue against an existing hash, if they do not match adds
   * serializedValue to keys and the new hash to updatedHashes.
   *
   * @param hashes          the existing hash values
   * @param id              the id of the item
   * @param serializedValue the serialized value
   * @param keys            values to persist - if the hash does not match id and serializedValue are appended
   * @param updatedHashes   hashes to persist - if the hash does not match adds an entry of id -> computed hash
   * @return true if the hash matched, false otherwise
   */
  private boolean hashCheck(Map<String, String> hashes, String id, String serializedValue, List<String> keys, Map<String, String> updatedHashes) {
    if (options.isHashingEnabled()) {
      final String hash = Hashing.sha1().newHasher().putString(serializedValue, UTF_8).hash().toString();
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

  protected MergeOp buildMergeOp(String type, CacheData cacheData, Map<String, String> hashes) {
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

    final Map<String, String> hashesToSet = new HashMap<>();
    final List<String> keysToSet = new ArrayList<>((cacheData.getRelationships().size() + 1) * 2);
    if (serializedAttributes != null &&
      hashCheck(hashes, attributesId(type, cacheData.getId()), serializedAttributes, keysToSet, hashesToSet)) {
      skippedWrites++;
    }

    if (!cacheData.getRelationships().isEmpty()) {
      for (Map.Entry<String, Collection<String>> relationship : cacheData.getRelationships().entrySet()) {
        final String relationshipValue;
        try {
          relationshipValue = objectMapper.writeValueAsString(new LinkedHashSet<>(relationship.getValue()));
        } catch (JsonProcessingException serializationException) {
          throw new RuntimeException("Relationship serialization failed", serializationException);
        }
        if (hashCheck(hashes, relationshipId(type, cacheData.getId(), relationship.getKey()), relationshipValue, keysToSet, hashesToSet)) {
          skippedWrites++;
        }
      }
    }

    return new MergeOp(cacheData.getRelationships().keySet(), keysToSet, hashesToSet, skippedWrites);
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

  protected List<String> getHashValues(List<String> hashKeys, String hashesId) {
    final List<String> hashValues = new ArrayList<>(hashKeys.size());
    redisClientDelegate.withCommandsClient(c -> {
      for (List<String> hashPart : Lists.partition(hashKeys, options.getMaxHmgetSize())) {
        hashValues.addAll(c.hmget(hashesId, Arrays.copyOf(hashPart.toArray(), hashPart.size(), String[].class)));
      }
    });
    return hashValues;
  }

  protected Map<String, String> getHashes(String type, Collection<CacheData> items) {
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

    return isHashingDisabled(type) ? Collections.emptyMap() : hashes;
  }

  private boolean isHashingDisabled(String type) {
     if (!options.isHashingEnabled()) {
        return true;
     }
     return redisClientDelegate.withCommandsClient(client -> {
       return client.exists(hashesDisabled(type));
     });
   }

  private Collection<CacheData> getItems(String type, List<String> ids, List<String> knownRels) {
    final int singleResultSize = knownRels.size() + 1;

    final List<String> keysToGet = new ArrayList<>(singleResultSize * ids.size());
    for (String id : ids) {
      keysToGet.add(attributesId(type, id));
      for (String rel : knownRels) {
        keysToGet.add(relationshipId(type, id, rel));
      }
    }

    final List<String> keyResult = new ArrayList<>(keysToGet.size());

    int mgetOperations = redisClientDelegate.withMultiClient(c -> {
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
      CacheData item = extractItem(idIterator.next(), keyResult.subList(ofs, ofs + singleResultSize), knownRels);
      if (item != null) {
        results.add(item);
      }
    }

    cacheMetrics.get(prefix, type, results.size(), ids.size(), keysToGet.size(), knownRels.size(), mgetOperations);
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
          Collection<String> deserializedRel = objectMapper.readValue(rel, RELATIONSHIPS);
          relationships.put(relType, deserializedRel);
        }
      }

      return new DefaultCacheData(id, attributes, relationships);

    } catch (IOException deserializationException) {
      throw new RuntimeException("Deserialization failed", deserializationException);
    }
  }

  protected String attributesId(String type, String id) {
    return String.format("%s:%s:attributes:%s", prefix, type, id);
  }

  protected String relationshipId(String type, String id, String relationship) {
    return String.format("%s:%s:relationships:%s:%s", prefix, type, id, relationship);
  }

  protected String hashesId(String type) {
    return String.format("%s:%s:hashes", prefix, type);
  }

  private String hashesDisabled(String type) {
    return String.format("%s:%s:hashes.disabled", prefix, type);
  }

  protected String allRelationshipsId(String type) {
    return String.format("%s:%s:relationships", prefix, type);
  }

  protected String allOfTypeId(String type) {
    return String.format("%s:%s:members", prefix, type);
  }

  protected String allOfTypeReindex(String type) {
    return String.format("%s:%s:members.2", prefix, type);
  }
}
