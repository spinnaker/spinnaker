/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.gate.banner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

/**
 * Manages all Redis I/O for global UI banners.
 *
 * <p>Key schema (prefix is configurable via {@code global-banner.redis-key-prefix}):
 *
 * <pre>
 *   {prefix}:{id}   STRING → JSON BannerRecord
 * </pre>
 *
 * <p>All banner records are stored as flat JSON strings. Enumeration uses {@code SCAN} rather than
 * {@code KEYS *} so it never blocks the Redis event loop in production.
 */
@Slf4j
public class RedisBannerRepository {

  private final JedisPool jedisPool;
  private final ObjectMapper objectMapper;
  private final String keyPrefix;

  public RedisBannerRepository(JedisPool jedisPool, ObjectMapper objectMapper, String keyPrefix) {
    this.jedisPool = jedisPool;
    this.objectMapper = objectMapper;
    this.keyPrefix = keyPrefix;
  }

  // ---------------------------------------------------------------------------
  // Write operations
  // ---------------------------------------------------------------------------

  /**
   * Persist (upsert) a banner record. Overwrites any existing record with the same {@code id}.
   *
   * @throws IllegalArgumentException if the record's {@code id} is null or blank
   */
  public void save(BannerRecord record) {
    if (record.getId() == null || record.getId().isBlank()) {
      throw new IllegalArgumentException("BannerRecord.id must not be null or blank");
    }
    String key = key(record.getId());
    String json = toJson(record);
    try (Jedis jedis = jedisPool.getResource()) {
      jedis.set(key, json);
    }
    log.debug("Saved banner '{}' to Redis key '{}'", record.getId(), key);
  }

  /**
   * Delete the banner with the given {@code id}.
   *
   * @return {@code true} if the key existed and was deleted; {@code false} if it was not found
   */
  public boolean delete(String id) {
    String key = key(id);
    try (Jedis jedis = jedisPool.getResource()) {
      long deleted = jedis.del(key);
      if (deleted > 0) {
        log.debug("Deleted banner '{}' from Redis", id);
        return true;
      }
      return false;
    }
  }

  /**
   * Delete all banners whose keys match the configured key prefix.
   *
   * @return the number of keys deleted
   */
  public int deleteAll() {
    List<String> keys = scanKeys();
    if (keys.isEmpty()) {
      return 0;
    }
    try (Jedis jedis = jedisPool.getResource()) {
      Pipeline pipeline = jedis.pipelined();
      List<Response<Long>> responses = new ArrayList<>(keys.size());
      for (String key : keys) {
        responses.add(pipeline.del(key));
      }
      pipeline.sync();
      int deleted = responses.stream().mapToInt(r -> r.get().intValue()).sum();
      log.debug("Deleted {} banners from Redis", deleted);
      return deleted;
    }
  }

  // ---------------------------------------------------------------------------
  // Read operations
  // ---------------------------------------------------------------------------

  /**
   * Retrieve a single banner by id.
   *
   * @return an {@link Optional} containing the record, or empty if not found
   */
  public Optional<BannerRecord> findById(String id) {
    try (Jedis jedis = jedisPool.getResource()) {
      String json = jedis.get(key(id));
      if (json == null) {
        return Optional.empty();
      }
      return Optional.of(fromJson(json));
    }
  }

  /**
   * Retrieve all banners stored under the configured key prefix, using {@code SCAN} for
   * production-safe enumeration.
   *
   * @return a list of all stored {@link BannerRecord}s (may be empty, never null)
   */
  public List<BannerRecord> findAll() {
    List<String> keys = scanKeys();
    if (keys.isEmpty()) {
      return List.of();
    }
    try (Jedis jedis = jedisPool.getResource()) {
      Pipeline pipeline = jedis.pipelined();
      List<Response<String>> responses = new ArrayList<>(keys.size());
      for (String key : keys) {
        responses.add(pipeline.get(key));
      }
      pipeline.sync();

      List<BannerRecord> results = new ArrayList<>(responses.size());
      for (Response<String> response : responses) {
        String json = response.get();
        if (json != null) {
          results.add(fromJson(json));
        }
      }
      return results;
    }
  }

  // ---------------------------------------------------------------------------
  // Internal helpers
  // ---------------------------------------------------------------------------

  /** Enumerates all Redis keys under the banner key prefix using iterative SCAN. */
  private List<String> scanKeys() {
    String pattern = keyPrefix + ":*";
    List<String> keys = new ArrayList<>();
    try (Jedis jedis = jedisPool.getResource()) {
      String cursor = ScanParams.SCAN_POINTER_START;
      ScanParams params = new ScanParams().match(pattern).count(100);
      do {
        ScanResult<String> result = jedis.scan(cursor, params);
        keys.addAll(result.getResult());
        cursor = result.getCursor();
      } while (!cursor.equals(ScanParams.SCAN_POINTER_START));
    }
    return keys;
  }

  private String key(String id) {
    return keyPrefix + ":" + id;
  }

  private String toJson(BannerRecord record) {
    try {
      return objectMapper.writeValueAsString(record);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialise BannerRecord id=" + record.getId(), e);
    }
  }

  private BannerRecord fromJson(String json) {
    try {
      return objectMapper.readValue(json, BannerRecord.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to deserialise BannerRecord: " + json, e);
    }
  }
}
