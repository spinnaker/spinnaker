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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.CacheFilter;
import com.netflix.spinnaker.cats.cache.WriteableCache;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Response;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

public abstract class AbstractRedisCache implements WriteableCache {

  private static final TypeReference<List<String>> RELATIONSHIPS_LIST =
      new TypeReference<List<String>>() {};
  private static final TypeReference<Set<String>> RELATIONSHIPS_SET =
      new TypeReference<Set<String>>() {};

  protected static final TypeReference<Map<String, Object>> ATTRIBUTES =
      new TypeReference<Map<String, Object>>() {};

  private final Logger log = LoggerFactory.getLogger(getClass());

  protected final String prefix;
  protected final RedisClientDelegate redisClientDelegate;
  protected final ObjectMapper objectMapper;
  protected final RedisCacheOptions options;

  protected AbstractRedisCache(
      String prefix,
      RedisClientDelegate redisClientDelegate,
      ObjectMapper objectMapper,
      RedisCacheOptions options) {
    this.prefix = prefix;
    this.redisClientDelegate = redisClientDelegate;
    this.objectMapper = objectMapper;
    this.options = options;
  }

  protected abstract void mergeItems(String type, Collection<CacheData> items);

  protected abstract void evictItems(
      String type, List<String> identifiers, Collection<String> allRelationships);

  protected abstract Collection<CacheData> getItems(
      String type, List<String> ids, List<String> knownRels);

  @Override
  public void merge(String type, CacheData item) {
    mergeAll(type, Arrays.asList(item));
  }

  @Override
  public void mergeAll(String type, Collection<CacheData> items) {
    for (List<CacheData> partition : Iterables.partition(items, options.getMaxMergeBatchSize())) {
      mergeItems(type, partition);
    }
  }

  @Override
  public void evict(String type, String id) {
    evictAll(type, Arrays.asList(id));
  }

  @Override
  public void evictAll(String type, Collection<String> identifiers) {
    if (identifiers.isEmpty()) {
      return;
    }
    final Collection<String> allRelationships = scanMembers(allRelationshipsId(type));
    for (List<String> items :
        Iterables.partition(new HashSet<>(identifiers), options.getMaxEvictBatchSize())) {
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
  public Collection<String> existingIdentifiers(String type, Collection<String> identifiers) {
    final Map<String, Response<Boolean>> responses = new LinkedHashMap<>();
    redisClientDelegate.withPipeline(
        p -> {
          for (String id : identifiers) {
            responses.put(id, p.exists(attributesId(type, id)));
          }
          redisClientDelegate.syncPipeline(p);
        });

    return responses.entrySet().stream()
        .filter(e -> e.getValue().get())
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());
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
  public Collection<CacheData> getAll(
      String type, Collection<String> identifiers, CacheFilter cacheFilter) {
    if (identifiers.isEmpty()) {
      return new ArrayList<>();
    }
    Collection<String> ids = new LinkedHashSet<>(identifiers);
    final List<String> knownRels;
    Set<String> allRelationships = scanMembers(allRelationshipsId(type));
    if (cacheFilter == null) {
      knownRels = new ArrayList<>(allRelationships);
    } else {
      knownRels =
          new ArrayList<>(cacheFilter.filter(CacheFilter.Type.RELATIONSHIP, allRelationships));
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

  protected Set<String> scanMembers(String setKey, Optional<String> glob) {
    return redisClientDelegate.withCommandsClient(
        client -> {
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

  protected boolean isHashingDisabled(String type) {
    if (!options.isHashingEnabled()) {
      return true;
    }
    return redisClientDelegate.withCommandsClient(
        client -> {
          return client.exists(hashesDisabled(type));
        });
  }

  protected String attributesId(String type, String id) {
    return String.format("%s:%s:attributes:%s", prefix, type, id);
  }

  protected String relationshipId(String type, String id, String relationship) {
    return String.format("%s:%s:relationships:%s:%s", prefix, type, id, relationship);
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

  protected TypeReference getRelationshipsTypeReference() {
    return options.isTreatRelationshipsAsSet() ? RELATIONSHIPS_SET : RELATIONSHIPS_LIST;
  }
}
