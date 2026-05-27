/*
 * Copyright 2026 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.security.apitoken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Transaction;

class RedisApiTokenRepositoryTest {

  private static EmbeddedRedis embeddedRedis;
  private static JedisPool jedisPool;

  private RedisApiTokenRepository repo;

  @BeforeAll
  static void startRedis() {
    embeddedRedis = EmbeddedRedis.embed();
    jedisPool = embeddedRedis.getPool();
  }

  @AfterAll
  static void stopRedis() {
    embeddedRedis.destroy();
  }

  @BeforeEach
  void setUp() {
    try (Jedis jedis = jedisPool.getResource()) {
      jedis.flushAll();
    }
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    repo = new RedisApiTokenRepository(jedisPool, mapper, "api-token");
  }

  // ---------------------------------------------------------------------------
  // save / findByHash / findById
  // ---------------------------------------------------------------------------

  @Test
  void save_and_findByHash_roundTrip() {
    TokenRecord record = buildRecord("tok-1", "USER", "alice", null);
    repo.save(record, "hash-abc");

    Optional<TokenRecord> found = repo.findByHash("hash-abc");
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo("tok-1");
    assertThat(found.get().getHashRef()).isEqualTo("hash-abc");
  }

  @Test
  void save_and_findById_roundTrip() {
    TokenRecord record = buildRecord("tok-2", "USER", "alice", null);
    repo.save(record, "hash-xyz");

    Optional<TokenRecord> found = repo.findById("tok-2");
    assertThat(found).isPresent();
    assertThat(found.get().getPrincipalId()).isEqualTo("alice");
  }

  @Test
  void findByHash_returnsEmpty_whenKeyAbsent() {
    assertThat(repo.findByHash("no-such-hash")).isEmpty();
  }

  @Test
  void findById_returnsEmpty_whenKeyAbsent() {
    assertThat(repo.findById("no-such-id")).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // findByPrincipal — basic listing
  // ---------------------------------------------------------------------------

  @Test
  void findByPrincipal_returnsAllLiveTokens() {
    repo.save(buildRecord("tok-a", "USER", "bob", null), "h-a");
    repo.save(buildRecord("tok-b", "USER", "bob", null), "h-b");

    List<TokenRecord> tokens = repo.findByPrincipal("USER", "bob");
    assertThat(tokens).extracting(TokenRecord::getId).containsExactlyInAnyOrder("tok-a", "tok-b");
  }

  @Test
  void findByPrincipal_returnsEmpty_whenNoneExist() {
    assertThat(repo.findByPrincipal("USER", "nobody")).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // findByPrincipal — ZSET self-cleaning (expired members pruned lazily)
  // ---------------------------------------------------------------------------

  @Test
  void findByPrincipal_prunesExpiredZsetMembers() {
    // expiresAt in the past → ZSET score will be a small epoch (year 1970)
    String pastExpiry = Instant.ofEpochSecond(1).toString();
    repo.save(buildRecord("expired-tok", "USER", "carol", pastExpiry), "h-expired");

    // Live token with no expiry
    repo.save(buildRecord("live-tok", "USER", "carol", null), "h-live");

    // findByPrincipal calls ZREMRANGEBYSCORE key 0 now, which removes the expired member
    List<TokenRecord> tokens = repo.findByPrincipal("USER", "carol");
    assertThat(tokens).extracting(TokenRecord::getId).containsExactly("live-tok");
  }

  /**
   * {@code null} exercises the {@code Long.MAX_VALUE} ZSET-score branch (non-expiring), the future
   * timestamp the normal branch.
   */
  @ParameterizedTest(name = "findByPrincipal keeps live token with expiresAt={0}")
  @MethodSource("liveExpiryFixtures")
  void findByPrincipal_keepsLiveTokens(String expiresAt) {
    repo.save(buildRecord("live-tok", "USER", "dave", expiresAt), "h-live");

    List<TokenRecord> tokens = repo.findByPrincipal("USER", "dave");
    assertThat(tokens).extracting(TokenRecord::getId).containsExactly("live-tok");
  }

  private static java.util.stream.Stream<Arguments> liveExpiryFixtures() {
    return java.util.stream.Stream.of(
        Arguments.of((String) null),
        Arguments.of(Instant.now().plus(365, ChronoUnit.DAYS).toString()));
  }

  // ---------------------------------------------------------------------------
  // findAllByPrincipalType
  // ---------------------------------------------------------------------------

  @Test
  void findAllByPrincipalType_returnsAllTokensForType() {
    repo.save(buildRecord("u1", "USER", "alice", null), "h-u1");
    repo.save(buildRecord("u2", "USER", "bob", null), "h-u2");
    repo.save(buildRecord("sa1", "SERVICE_ACCOUNT", "deploy-sa", null), "h-sa1");

    List<TokenRecord> userTokens = repo.findAllByPrincipalType("USER");
    assertThat(userTokens).extracting(TokenRecord::getId).containsExactlyInAnyOrder("u1", "u2");

    List<TokenRecord> saTokens = repo.findAllByPrincipalType("SERVICE_ACCOUNT");
    assertThat(saTokens).extracting(TokenRecord::getId).containsExactly("sa1");
  }

  @Test
  void findAllByPrincipalType_prunesExpiredMembers() {
    String pastExpiry = Instant.ofEpochSecond(1).toString();
    repo.save(buildRecord("expired-sa", "SERVICE_ACCOUNT", "old-sa", pastExpiry), "h-exp-sa");
    repo.save(buildRecord("live-sa", "SERVICE_ACCOUNT", "live-sa", null), "h-live-sa");

    List<TokenRecord> saTokens = repo.findAllByPrincipalType("SERVICE_ACCOUNT");
    assertThat(saTokens).extracting(TokenRecord::getId).containsExactly("live-sa");
  }

  @Test
  void findAllByPrincipalType_spansMultiplePrincipals() {
    repo.save(buildRecord("sa-a1", "SERVICE_ACCOUNT", "sa-alpha", null), "h-sa-a1");
    repo.save(buildRecord("sa-a2", "SERVICE_ACCOUNT", "sa-alpha", null), "h-sa-a2");
    repo.save(buildRecord("sa-b1", "SERVICE_ACCOUNT", "sa-beta", null), "h-sa-b1");

    List<TokenRecord> saTokens = repo.findAllByPrincipalType("SERVICE_ACCOUNT");
    assertThat(saTokens)
        .extracting(TokenRecord::getId)
        .containsExactlyInAnyOrder("sa-a1", "sa-a2", "sa-b1");
  }

  @Test
  void findAllByPrincipalType_returnsEmpty_whenNoneExist() {
    assertThat(repo.findAllByPrincipalType("SERVICE_ACCOUNT")).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // delete
  // ---------------------------------------------------------------------------

  @Test
  void delete_removesAllKeysIncludingNameReservation() {
    TokenRecord record = buildRecord("del-tok", "USER", "frank", null);
    record.setName("revokable-name");
    repo.save(record, "h-del");

    repo.delete("del-tok", "h-del", "revokable-name", "USER", "frank");

    assertThat(repo.findByHash("h-del")).isEmpty();
    assertThat(repo.findById("del-tok")).isEmpty();
    assertThat(repo.findByPrincipal("USER", "frank")).isEmpty();

    // The name reservation must be released so the principal can reuse the name (a non-expiring
    // token's EXPIREAT would never fire on its own).
    try (Jedis jedis = jedisPool.getResource()) {
      assertThat(jedis.exists(repo.nameKey("USER", "frank", "revokable-name"))).isFalse();
    }
  }

  @Test
  void delete_toleratesNullNameForLegacyRecords() {
    TokenRecord record = buildRecord("legacy-tok", "USER", "frank", null);
    record.setName("legacy-name");
    repo.save(record, "h-legacy");

    // Caller passes a null name (e.g. legacy record loaded from before the name index existed).
    // delete() must not throw; the other three keys must still be removed.
    repo.delete("legacy-tok", "h-legacy", null, "USER", "frank");

    assertThat(repo.findByHash("h-legacy")).isEmpty();
    assertThat(repo.findById("legacy-tok")).isEmpty();
    assertThat(repo.findByPrincipal("USER", "frank")).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // updateLastUsed
  // ---------------------------------------------------------------------------

  @Test
  void updateLastUsed_updatesTimestampOnBothKeys() {
    repo.save(buildRecord("upd-tok", "USER", "heidi", null), "h-upd");

    Instant newTs = Instant.parse("2026-06-01T00:00:00Z");
    repo.updateLastUsed("upd-tok", "h-upd", newTs);

    TokenRecord fromId = repo.findById("upd-tok").orElseThrow();
    assertThat(fromId.getLastUsedAt()).isEqualTo(newTs.toString());

    TokenRecord fromHash = repo.findByHash("h-upd").orElseThrow();
    assertThat(fromHash.getLastUsedAt()).isEqualTo(newTs.toString());
  }

  @Test
  void updateLastUsed_isNoOp_whenKeyAbsent() {
    // should not throw
    repo.updateLastUsed("ghost-id", "ghost-hash", Instant.now());
  }

  // ---------------------------------------------------------------------------
  // updateLastFiatCheck
  // ---------------------------------------------------------------------------

  @Test
  void updateLastFiatCheck_updatesTimestampOnBothKeys() {
    repo.save(buildRecord("fiat-tok", "USER", "ivan", null), "h-fiat");

    Instant checkedAt = Instant.parse("2026-06-01T12:00:00Z");
    repo.updateLastFiatCheck("fiat-tok", "h-fiat", checkedAt);

    TokenRecord fromId = repo.findById("fiat-tok").orElseThrow();
    assertThat(fromId.getLastFiatCheckAt()).isEqualTo(checkedAt.toString());

    TokenRecord fromHash = repo.findByHash("h-fiat").orElseThrow();
    assertThat(fromHash.getLastFiatCheckAt()).isEqualTo(checkedAt.toString());
  }

  /**
   * Regression: a naive read-modify-write of the JSON would let concurrent updateLastUsed and
   * updateLastFiatCheck clobber each other's sibling field. With WATCH/MULTI/EXEC + retry, both
   * threads' final values must be observable on both keys.
   */
  @Test
  void concurrentUpdates_doNotClobberSiblingFields() throws Exception {
    repo.save(buildRecord("race-tok", "USER", "judy", null), "h-race");

    int iterations = 200;
    Instant base = Instant.parse("2026-06-01T00:00:00Z");

    ExecutorService exec = Executors.newFixedThreadPool(2);
    try {
      CountDownLatch start = new CountDownLatch(1);

      exec.submit(
          () -> {
            start.await();
            for (int i = 0; i < iterations; i++) {
              repo.updateLastUsed("race-tok", "h-race", base.plusSeconds(i));
            }
            return null;
          });
      exec.submit(
          () -> {
            start.await();
            for (int i = 0; i < iterations; i++) {
              repo.updateLastFiatCheck("race-tok", "h-race", base.plusSeconds(i));
            }
            return null;
          });

      start.countDown();
      exec.shutdown();
      assertThat(exec.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    } finally {
      if (!exec.isTerminated()) {
        exec.shutdownNow();
      }
    }

    Instant lastValueWritten = base.plusSeconds(iterations - 1);

    TokenRecord fromId = repo.findById("race-tok").orElseThrow();
    assertThat(fromId.getLastUsedAt())
        .as("lastUsedAt must not be clobbered by a concurrent updateLastFiatCheck")
        .isEqualTo(lastValueWritten.toString());
    assertThat(fromId.getLastFiatCheckAt())
        .as("lastFiatCheckAt must not be clobbered by a concurrent updateLastUsed")
        .isEqualTo(lastValueWritten.toString());

    TokenRecord fromHash = repo.findByHash("h-race").orElseThrow();
    assertThat(fromHash.getLastUsedAt()).isEqualTo(lastValueWritten.toString());
    assertThat(fromHash.getLastFiatCheckAt()).isEqualTo(lastValueWritten.toString());
  }

  // ---------------------------------------------------------------------------
  // save — tokenHash precondition (write-side invariant enforcement)
  // ---------------------------------------------------------------------------

  @Test
  void save_rejectsNullTokenHash() {
    TokenRecord record = buildRecord("null-hash-tok", "USER", "nadia", null);
    assertThatThrownBy(() -> repo.save(record, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("tokenHash");

    // The rejected save must write nothing — a null hashRef on persisted state would be
    // unrevokable from the hash index.
    assertThat(repo.findById("null-hash-tok")).isEmpty();
    assertThat(repo.findByPrincipal("USER", "nadia")).isEmpty();
  }

  @Test
  void save_rejectsBlankTokenHash() {
    TokenRecord record = buildRecord("blank-hash-tok", "USER", "oscar", null);
    assertThatThrownBy(() -> repo.save(record, "   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tokenHash");

    assertThat(repo.findById("blank-hash-tok")).isEmpty();
    assertThat(repo.findByPrincipal("USER", "oscar")).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // save — name uniqueness (atomic SETNX on the name index key)
  // ---------------------------------------------------------------------------

  @Test
  void save_rejectsSecondTokenWithSameNameForSamePrincipal() {
    TokenRecord first = buildRecord("first-id", "USER", "kate", null);
    first.setName("shared-name");
    repo.save(first, "h-first");

    TokenRecord second = buildRecord("second-id", "USER", "kate", null);
    second.setName("shared-name");

    assertThatThrownBy(() -> repo.save(second, "h-second"))
        .isInstanceOf(RedisApiTokenRepository.DuplicateTokenNameException.class)
        .hasMessageContaining("shared-name");

    // The losing save must write none of its own keys — otherwise we'd leave a ghost
    // authenticatable token or a dangling uuid in the principal ZSET.
    assertThat(repo.findByHash("h-second")).isEmpty();
    assertThat(repo.findById("second-id")).isEmpty();
    assertThat(repo.findByPrincipal("USER", "kate"))
        .extracting(TokenRecord::getId)
        .containsExactly("first-id");
  }

  @Test
  void save_allowsSameNameForDifferentPrincipals() {
    TokenRecord forAlice = buildRecord("alice-tok", "USER", "alice", null);
    forAlice.setName("shared-name");
    repo.save(forAlice, "h-alice-shared");

    TokenRecord forBob = buildRecord("bob-tok", "USER", "bob", null);
    forBob.setName("shared-name");
    // Different principal → different name index key → must succeed.
    repo.save(forBob, "h-bob-shared");

    assertThat(repo.findById("alice-tok")).isPresent();
    assertThat(repo.findById("bob-tok")).isPresent();
  }

  @Test
  void save_releasesNameAfterDeleteSoSamePrincipalCanReuseIt() {
    TokenRecord first = buildRecord("first-id", "USER", "leo", null);
    first.setName("recyclable");
    repo.save(first, "h-first-recyclable");

    repo.delete("first-id", "h-first-recyclable", "recyclable", "USER", "leo");

    TokenRecord recreated = buildRecord("second-id", "USER", "leo", null);
    recreated.setName("recyclable");
    // Must not throw — the previous delete released the name reservation.
    repo.save(recreated, "h-second-recyclable");

    assertThat(repo.findById("second-id")).isPresent();
  }

  /**
   * Regression: out of N concurrent saves with the same (principal, name), the atomic SETNX must
   * let exactly one succeed and the rest throw {@link
   * RedisApiTokenRepository.DuplicateTokenNameException}.
   */
  @Test
  void concurrentSavesWithSameName_exactlyOneWins() throws Exception {
    int contenders = 8;
    ExecutorService exec = Executors.newFixedThreadPool(contenders);
    try {
      CountDownLatch start = new CountDownLatch(1);
      AtomicInteger successes = new AtomicInteger();
      AtomicInteger duplicates = new AtomicInteger();
      ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

      List<Future<?>> futures = new java.util.ArrayList<>(contenders);
      for (int i = 0; i < contenders; i++) {
        final int idx = i;
        futures.add(
            exec.submit(
                () -> {
                  try {
                    start.await();
                    TokenRecord record = buildRecord("race-id-" + idx, "USER", "mallory", null);
                    record.setName("contested-name");
                    repo.save(record, "h-race-" + idx);
                    successes.incrementAndGet();
                  } catch (RedisApiTokenRepository.DuplicateTokenNameException expected) {
                    duplicates.incrementAndGet();
                  } catch (Throwable t) {
                    unexpected.add(t);
                  }
                  return null;
                }));
      }

      start.countDown();
      for (Future<?> f : futures) {
        f.get(30, TimeUnit.SECONDS);
      }

      assertThat(unexpected).as("only DuplicateTokenNameException is expected").isEmpty();
      assertThat(successes.get()).as("exactly one save must commit").isEqualTo(1);
      assertThat(duplicates.get())
          .as("every loser must throw so the controller can return 409")
          .isEqualTo(contenders - 1);
      assertThat(repo.findByPrincipal("USER", "mallory"))
          .as("exactly one token persisted")
          .hasSize(1);
    } finally {
      exec.shutdownNow();
    }
  }

  // ---------------------------------------------------------------------------
  // save — MULTI/EXEC atomicity
  // ---------------------------------------------------------------------------

  /**
   * If EXEC returns null mid-save, no partial state (notably a readable hash key without its id
   * record) may survive — that would be an authenticatable but unrevokable token.
   */
  @Test
  void save_throwsAndWritesNothing_whenExecReturnsNull() {
    // Real Jedis (so SET NX really reserves the name), but multi() returns a Transaction whose
    // exec() returns null.
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    JedisPool spyPool = mock(JedisPool.class);
    Jedis realJedis = jedisPool.getResource();
    Jedis spyJedis = spy(realJedis);
    Transaction fakeTx = mock(Transaction.class);
    when(fakeTx.exec()).thenReturn(null);
    doReturn(fakeTx).when(spyJedis).multi();
    doAnswer(
            inv -> {
              realJedis.close();
              return null;
            })
        .when(spyJedis)
        .close();
    when(spyPool.getResource()).thenReturn(spyJedis);

    RedisApiTokenRepository failingRepo = new RedisApiTokenRepository(spyPool, mapper, "api-token");

    TokenRecord record = buildRecord("aborted-tok", "USER", "ursula", null);
    record.setName("aborted-name");

    assertThatThrownBy(() -> failingRepo.save(record, "h-aborted"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("EXEC returned null");

    assertThat(repo.findByHash("h-aborted")).isEmpty();
    assertThat(repo.findById("aborted-tok")).isEmpty();
    assertThat(repo.findByPrincipal("USER", "ursula")).isEmpty();
    try (Jedis jedis = jedisPool.getResource()) {
      assertThat(jedis.exists(repo.nameKey("USER", "ursula", "aborted-name"))).isFalse();
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static TokenRecord buildRecord(
      String id, String principalType, String principalId, String expiresAt) {
    TokenRecord r = new TokenRecord();
    r.setId(id);
    r.setName("test-token-" + id);
    r.setPrincipalType(principalType);
    r.setPrincipalId(principalId);
    r.setCreatedByUserId(principalId);
    r.setCreatedAt(Instant.now().toString());
    r.setExpiresAt(expiresAt);
    return r;
  }
}
