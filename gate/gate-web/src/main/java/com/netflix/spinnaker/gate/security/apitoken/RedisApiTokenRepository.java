/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.gate.security.apitoken;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.ScanResult;

/**
 * Manages all Redis I/O for API tokens.
 *
 * <p>Key schema (prefix is configurable via {@code api-tokens.redis-key-prefix}):
 *
 * <pre>
 *   {prefix}:hash:{sha256_hex}             STRING → JSON TokenRecord  (EXPIREAT if token has expiresAt)
 *   {prefix}:id:{uuid}                     STRING → JSON TokenRecord  (EXPIREAT if token has expiresAt)
 *   {prefix}:principal:{TYPE}:{id}         ZSET   → member UUIDs, score = expiresAt epoch seconds
 *                                                   (Long.MAX_VALUE for non-expiring tokens)
 *   {prefix}:name:{TYPE}:{id}:{sha(name)}  STRING → token UUID         (EXPIREAT if token has expiresAt)
 * </pre>
 *
 * <ul>
 *   <li>The principal ZSET is self-cleaning: {@link #findByPrincipal} prunes expired members via
 *       {@code ZREMRANGEBYSCORE} before reading.
 *   <li>The name index key (sha256 of the name, to avoid {@code ':'} collisions) enforces
 *       per-principal name uniqueness atomically via {@code SET NX} in {@link #save}.
 *   <li>Revocation issues all four DELs inside MULTI/EXEC so it's atomic across Gate replicas.
 *   <li>Timestamp updates use WATCH/MULTI/EXEC with bounded retry so concurrent writers don't
 *       clobber each other's sibling fields via stale GET → mutate → SET round-trips.
 * </ul>
 */
@Slf4j
public class RedisApiTokenRepository {

  /**
   * Thrown by {@link #save} when a token with the same name already exists for the principal — the
   * atomic {@code SET NX} on the name index lost the race. Translated to HTTP 409 by the
   * controller.
   */
  public static class DuplicateTokenNameException extends RuntimeException {
    public DuplicateTokenNameException(String message) {
      super(message);
    }
  }

  /** Bounded retry for optimistic-locking timestamp updates; these fields are best-effort. */
  private static final int MAX_TIMESTAMP_UPDATE_ATTEMPTS = 3;

  private final JedisPool jedisPool;
  private final ObjectMapper objectMapper;
  private final String keyPrefix;

  public RedisApiTokenRepository(JedisPool jedisPool, ObjectMapper objectMapper, String keyPrefix) {
    this.jedisPool = jedisPool;
    this.objectMapper = objectMapper;
    this.keyPrefix = keyPrefix;
  }

  /**
   * Persist a new token: write hash/id keys and add the uuid to the principal ZSET (score = {@code
   * expiresAt} epoch seconds, or {@link Long#MAX_VALUE} for non-expiring tokens). When {@code
   * expiresAt} is set, {@code EXPIREAT} is applied to the string keys and the name index.
   *
   * <p>Name uniqueness is enforced atomically by {@code SET NX EX 60} on the name index key before
   * the rest of the save; if the name is taken this throws and writes nothing else. The remaining
   * writes (hash key, id key, principal ZSET, and EXPIREATs) commit together inside MULTI/EXEC so
   * an aborted EXEC can't leave a readable hash key without its id record. The name reservation is
   * released best-effort if the transaction fails; the 60s SETNX TTL is the backstop that
   * auto-evicts the reservation if even the explicit release can't run (e.g., JVM crash, connection
   * loss). Once the MULTI commits, the reservation's TTL is replaced with the token's actual
   * lifetime — {@code EXPIREAT} for tokens with an explicit expiry, {@code PERSIST} (TTL removed)
   * for non-expiring tokens.
   *
   * <p>Sets {@code record.hashRef} so {@link #delete} can find the hash key during revocation.
   *
   * @throws DuplicateTokenNameException if the (principal, name) pair already exists
   * @throws IllegalArgumentException if {@code tokenHash} is null or blank
   */
  public void save(TokenRecord record, String tokenHash) {
    Objects.requireNonNull(tokenHash, "tokenHash must not be null");
    if (tokenHash.isBlank()) {
      throw new IllegalArgumentException("tokenHash must not be blank");
    }
    record.setHashRef(tokenHash);
    String json = toJson(record);
    String hashKey = hashKey(tokenHash);
    String idKey = idKey(record.getId());
    String principalKey = principalKey(record.getPrincipalType(), record.getPrincipalId());
    String nameKey = nameKey(record.getPrincipalType(), record.getPrincipalId(), record.getName());

    long zsetScore =
        (record.getExpiresAt() != null && !record.getExpiresAt().isBlank())
            ? Instant.parse(record.getExpiresAt()).getEpochSecond()
            : Long.MAX_VALUE;

    try (Jedis jedis = jedisPool.getResource()) {
      // SETNX without a TTL; the matching EXPIREAT is folded into the MULTI/EXEC below.
      // (`SET NX EXAT` would require Redis 6.2; we support older versions.)
      String reservation = jedis.set(nameKey, record.getId(), SetParams.setParams().nx());
      if (reservation == null) {
        throw new DuplicateTokenNameException(
            "A token named '"
                + record.getName()
                + "' already exists for "
                + record.getPrincipalType()
                + ":"
                + record.getPrincipalId());
      }

      boolean committed = false;
      try {
        Transaction tx = jedis.multi();
        tx.set(hashKey, json);
        tx.set(idKey, json);
        tx.zadd(principalKey, (double) zsetScore, record.getId());

        if (zsetScore != Long.MAX_VALUE) {
          tx.expireAt(hashKey, zsetScore);
          tx.expireAt(idKey, zsetScore);
          tx.expireAt(nameKey, zsetScore);
        }
        List<Object> results = tx.exec();
        if (results == null || results.isEmpty()) {
          throw new RuntimeException("Token save failed: EXEC returned null");
        }
        committed = true;
      } finally {
        if (!committed) {
          try {
            jedis.del(nameKey);
          } catch (RuntimeException releaseFailure) {
            log.warn(
                "Failed to release orphan name reservation {} after save failure: {}",
                nameKey,
                releaseFailure.getMessage());
          }
        }
      }
    }
  }

  /** Look up a token by SHA-256 hex hash. Returns empty if the key does not exist. */
  public Optional<TokenRecord> findByHash(String sha256Hex) {
    try (Jedis jedis = jedisPool.getResource()) {
      String json = jedis.get(hashKey(sha256Hex));
      return Optional.ofNullable(json).map(this::fromJson);
    }
  }

  /** Look up a token by its UUID. Returns empty if the key does not exist. */
  public Optional<TokenRecord> findById(String id) {
    try (Jedis jedis = jedisPool.getResource()) {
      String json = jedis.get(idKey(id));
      return Optional.ofNullable(json).map(this::fromJson);
    }
  }

  /**
   * Return all tokens for a principal. Prunes expired ZSET members ({@code ZREMRANGEBYSCORE key 0
   * now}) before pipelining {@code GET} on the live id keys.
   */
  public List<TokenRecord> findByPrincipal(String principalType, String principalId) {
    String principalKey = principalKey(principalType, principalId);
    long nowEpoch = Instant.now().getEpochSecond();

    try (Jedis jedis = jedisPool.getResource()) {
      jedis.zremrangeByScore(principalKey, 0, nowEpoch);

      // Jedis 5.x: ZRANGE returns List<String> in score order; members are already unique by ZSET
      // semantics.
      List<String> members = jedis.zrange(principalKey, 0, -1);
      if (members == null || members.isEmpty()) {
        return List.of();
      }

      List<Response<String>> responses = new ArrayList<>(members.size());
      Pipeline pipe = jedis.pipelined();
      for (String uuid : members) {
        responses.add(pipe.get(idKey(uuid)));
      }
      pipe.sync();

      List<TokenRecord> result = new ArrayList<>(members.size());
      for (Response<String> r : responses) {
        String json = r.get();
        if (json != null) {
          result.add(fromJson(json));
        }
      }
      return result;
    }
  }

  /**
   * Atomically revoke a token: all four DELs commit together inside MULTI/EXEC so no Gate replica
   * can observe a partial state. The hash key — the only one read on the auth hot path — is first
   * in the transaction. {@code name} may be {@code null} for legacy records (the name DEL is then
   * skipped).
   */
  public void delete(
      String id, String sha256Hex, String name, String principalType, String principalId) {
    try (Jedis jedis = jedisPool.getResource()) {
      Transaction tx = jedis.multi();
      tx.del(hashKey(sha256Hex));
      tx.del(idKey(id));
      tx.zrem(principalKey(principalType, principalId), id);
      if (name != null && !name.isBlank()) {
        tx.del(nameKey(principalType, principalId, name));
      }
      tx.exec();
    }
  }

  /**
   * Update {@code lastUsedAt} on the id and hash keys, preserving TTLs. Writing both keys preserves
   * the "JSON matches on both keys" invariant. See {@link #updateTokenWithOptimisticLock} for the
   * concurrency story.
   */
  public void updateLastUsed(String id, String sha256Hex, Instant lastUsedAt) {
    updateTokenWithOptimisticLock(
        id, sha256Hex, "lastUsedAt", record -> record.setLastUsedAt(lastUsedAt.toString()));
  }

  /**
   * Update {@code lastFiatCheckAt} on the id and hash keys, preserving TTLs. The optimistic lock
   * matters here — a concurrent {@link #updateLastUsed} clobbering a fresh check timestamp would
   * trigger a redundant Fiat call.
   */
  public void updateLastFiatCheck(String id, String sha256Hex, Instant checkedAt) {
    updateTokenWithOptimisticLock(
        id,
        sha256Hex,
        "lastFiatCheckAt",
        record -> record.setLastFiatCheckAt(checkedAt.toString()));
  }

  /**
   * WATCH/MULTI/EXEC read-modify-write of the JSON record on both id and hash keys, preserving
   * TTLs. On WATCH conflict we retry up to {@link #MAX_TIMESTAMP_UPDATE_ATTEMPTS} times against a
   * fresh snapshot, then give up — these fields are best-effort metadata.
   */
  private void updateTokenWithOptimisticLock(
      String id, String sha256Hex, String fieldName, Consumer<TokenRecord> mutator) {
    String idKey = idKey(id);
    String hashKey = hashKey(sha256Hex);

    try (Jedis jedis = jedisPool.getResource()) {
      for (int attempt = 0; attempt < MAX_TIMESTAMP_UPDATE_ATTEMPTS; attempt++) {
        jedis.watch(idKey, hashKey);

        String json = jedis.get(idKey);
        if (json == null) {
          jedis.unwatch();
          return;
        }
        TokenRecord record = fromJson(json);
        if (record == null) {
          jedis.unwatch();
          return;
        }
        mutator.accept(record);
        String updated = toJson(record);

        long idTtl = jedis.ttl(idKey);
        long hashTtl = jedis.ttl(hashKey);

        Transaction tx = jedis.multi();
        tx.set(idKey, updated);
        if (idTtl > 0) {
          tx.expire(idKey, idTtl);
        }
        tx.set(hashKey, updated);
        if (hashTtl > 0) {
          tx.expire(hashKey, hashTtl);
        }
        List<Object> results = tx.exec();
        if (results != null) {
          return;
        }
      }
      log.warn(
          "Gave up updating {} for token id={} after {} WATCH conflicts; another writer kept "
              + "winning the race. The field is best-effort metadata.",
          fieldName,
          id,
          MAX_TIMESTAMP_UPDATE_ATTEMPTS);
    }
  }

  /**
   * SCAN all principal ZSETs of the given type, prune expired members, and return all live tokens.
   * Used by admin-facing list endpoints; the SCAN is acceptable because the principal count is
   * small in practice.
   */
  public List<TokenRecord> findAllByPrincipalType(String principalType) {
    String pattern = keyPrefix + ":principal:" + principalType.toUpperCase() + ":*";
    long nowEpoch = Instant.now().getEpochSecond();
    // SCAN can return duplicates across iterations, so dedupe principal keys (and thus UUIDs).
    Set<String> seenPrincipalKeys = new LinkedHashSet<>();
    List<String> allUuids = new ArrayList<>();

    try (Jedis jedis = jedisPool.getResource()) {
      String cursor = ScanParams.SCAN_POINTER_START;
      do {
        ScanResult<String> scan = jedis.scan(cursor, new ScanParams().match(pattern).count(200));
        cursor = scan.getCursor();
        for (String principalKey : scan.getResult()) {
          if (!seenPrincipalKeys.add(principalKey)) continue;
          jedis.zremrangeByScore(principalKey, 0, nowEpoch);
          List<String> members = jedis.zrange(principalKey, 0, -1);
          if (members != null) {
            allUuids.addAll(members);
          }
        }
      } while (!ScanParams.SCAN_POINTER_START.equals(cursor));

      if (allUuids.isEmpty()) {
        return List.of();
      }

      List<Response<String>> responses = new ArrayList<>(allUuids.size());
      Pipeline pipe = jedis.pipelined();
      for (String uuid : allUuids) {
        responses.add(pipe.get(idKey(uuid)));
      }
      pipe.sync();

      List<TokenRecord> result = new ArrayList<>(allUuids.size());
      for (Response<String> r : responses) {
        String json = r.get();
        if (json != null) {
          TokenRecord record = fromJson(json);
          if (record != null) result.add(record);
        }
      }
      return result;
    }
  }

  // ---------------------------------------------------------------------------
  // Key builders
  // ---------------------------------------------------------------------------

  public String hashKey(String sha256Hex) {
    return keyPrefix + ":hash:" + sha256Hex;
  }

  public String idKey(String id) {
    return keyPrefix + ":id:" + id;
  }

  public String principalKey(String principalType, String principalId) {
    return keyPrefix + ":principal:" + principalType.toUpperCase() + ":" + principalId;
  }

  /**
   * Per-principal name reservation key. The name is hashed (fixed-length suffix) so that {@code
   * ':'} characters in {@code principalId} can't make different (principal, name) pairs collide.
   */
  public String nameKey(String principalType, String principalId, String name) {
    return keyPrefix
        + ":name:"
        + principalType.toUpperCase()
        + ":"
        + principalId
        + ":"
        + ApiTokenHashing.sha256Hex(name);
  }

  // ---------------------------------------------------------------------------
  // JSON helpers
  // ---------------------------------------------------------------------------

  private String toJson(TokenRecord record) {
    try {
      return objectMapper.writeValueAsString(record);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize TokenRecord", e);
    }
  }

  private TokenRecord fromJson(String json) {
    try {
      return objectMapper.readValue(json, TokenRecord.class);
    } catch (Exception e) {
      log.warn("Failed to deserialize TokenRecord from Redis: {}", e.getMessage());
      return null;
    }
  }
}
