/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.gate.banner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

class RedisBannerRepositoryTest {

  private static EmbeddedRedis embeddedRedis;
  private static JedisPool jedisPool;

  private RedisBannerRepository repo;

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
    repo = new RedisBannerRepository(jedisPool, new ObjectMapper(), "global-banner");
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private BannerRecord banner(String id, String message, boolean enabled) {
    BannerRecord b = new BannerRecord();
    b.setId(id);
    b.setMessage(message);
    b.setEnabled(enabled);
    b.setColor("#333333");
    b.setBackgroundColor("#fff3cd");
    return b;
  }

  // ---------------------------------------------------------------------------
  // save / findById
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("save and findById")
  class SaveAndFind {

    @Test
    @DisplayName("roundtrip preserves all fields including nullable timestamps")
    void roundtrip() {
      BannerRecord b = banner("b1", "Hello world", true);
      b.setStartTimestamp(1_000_000L);
      b.setEndTimestamp(9_000_000L);
      b.setCreatedAt("2026-01-01T00:00:00Z");
      b.setUpdatedAt("2026-01-02T00:00:00Z");

      repo.save(b);

      Optional<BannerRecord> found = repo.findById("b1");
      assertThat(found).isPresent();
      BannerRecord r = found.get();
      assertThat(r.getId()).isEqualTo("b1");
      assertThat(r.getMessage()).isEqualTo("Hello world");
      assertThat(r.isEnabled()).isTrue();
      assertThat(r.getColor()).isEqualTo("#333333");
      assertThat(r.getBackgroundColor()).isEqualTo("#fff3cd");
      assertThat(r.getStartTimestamp()).isEqualTo(1_000_000L);
      assertThat(r.getEndTimestamp()).isEqualTo(9_000_000L);
      assertThat(r.getCreatedAt()).isEqualTo("2026-01-01T00:00:00Z");
      assertThat(r.getUpdatedAt()).isEqualTo("2026-01-02T00:00:00Z");
    }

    @Test
    @DisplayName("roundtrip with null optional timestamps")
    void roundtripNullTimestamps() {
      repo.save(banner("b2", "No timestamps", false));

      Optional<BannerRecord> found = repo.findById("b2");
      assertThat(found).isPresent();
      assertThat(found.get().getStartTimestamp()).isNull();
      assertThat(found.get().getEndTimestamp()).isNull();
    }

    @Test
    @DisplayName("returns empty when key absent")
    void returnsEmptyWhenAbsent() {
      assertThat(repo.findById("nonexistent")).isEmpty();
    }

    @Test
    @DisplayName("save overwrites existing record (upsert)")
    void upsert() {
      repo.save(banner("b3", "original", true));
      BannerRecord updated = banner("b3", "updated", false);
      repo.save(updated);

      Optional<BannerRecord> found = repo.findById("b3");
      assertThat(found).isPresent();
      assertThat(found.get().getMessage()).isEqualTo("updated");
      assertThat(found.get().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("throws when id is null")
    void throwsOnNullId() {
      BannerRecord b = new BannerRecord();
      b.setMessage("no id");
      assertThatThrownBy(() -> repo.save(b)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("throws when id is blank")
    void throwsOnBlankId() {
      BannerRecord b = new BannerRecord();
      b.setId("   ");
      b.setMessage("blank id");
      assertThatThrownBy(() -> repo.save(b)).isInstanceOf(IllegalArgumentException.class);
    }
  }

  // ---------------------------------------------------------------------------
  // findAll
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("findAll")
  class FindAll {

    @Test
    @DisplayName("returns all saved banners")
    void returnsAll() {
      repo.save(banner("a1", "A1", true));
      repo.save(banner("a2", "A2", false));
      repo.save(banner("a3", "A3", true));

      List<BannerRecord> all = repo.findAll();
      assertThat(all).extracting(BannerRecord::getId).containsExactlyInAnyOrder("a1", "a2", "a3");
    }

    @Test
    @DisplayName("returns empty list when no banners stored")
    void returnsEmptyWhenNone() {
      assertThat(repo.findAll()).isEmpty();
    }

    @Test
    @DisplayName("only returns banners under the configured prefix")
    void respectsPrefix() {
      // Write a key outside our prefix directly
      try (Jedis jedis = jedisPool.getResource()) {
        jedis.set("other-prefix:intruder", "{\"id\":\"intruder\"}");
      }
      repo.save(banner("mine", "mine", true));

      List<BannerRecord> all = repo.findAll();
      assertThat(all).extracting(BannerRecord::getId).containsExactly("mine");
    }
  }

  // ---------------------------------------------------------------------------
  // delete
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("delete")
  class Delete {

    @Test
    @DisplayName("returns true and removes the key when banner exists")
    void deletesExisting() {
      repo.save(banner("d1", "delete me", true));
      assertThat(repo.delete("d1")).isTrue();
      assertThat(repo.findById("d1")).isEmpty();
    }

    @Test
    @DisplayName("returns false when banner does not exist")
    void returnsFalseWhenAbsent() {
      assertThat(repo.delete("ghost")).isFalse();
    }
  }

  // ---------------------------------------------------------------------------
  // deleteAll
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("deleteAll")
  class DeleteAll {

    @Test
    @DisplayName("deletes all banners and returns count")
    void deletesAllAndReturnsCount() {
      repo.save(banner("x1", "X1", true));
      repo.save(banner("x2", "X2", true));

      int deleted = repo.deleteAll();
      assertThat(deleted).isEqualTo(2);
      assertThat(repo.findAll()).isEmpty();
    }

    @Test
    @DisplayName("returns 0 when no banners exist")
    void returnsZeroWhenEmpty() {
      assertThat(repo.deleteAll()).isZero();
    }

    @Test
    @DisplayName("does not delete keys outside the configured prefix")
    void doesNotDeleteOtherPrefixes() {
      try (Jedis jedis = jedisPool.getResource()) {
        jedis.set("other-prefix:safe", "safe-value");
      }
      repo.save(banner("mine", "mine", true));

      repo.deleteAll();

      try (Jedis jedis = jedisPool.getResource()) {
        assertThat(jedis.get("other-prefix:safe")).isEqualTo("safe-value");
      }
    }
  }
}
