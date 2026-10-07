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
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GlobalBannerServiceTest {

  @Mock RedisBannerRepository repository;

  GlobalBannerService service;

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private BannerRecord activeBanner(String id) {
    BannerRecord b = new BannerRecord();
    b.setId(id);
    b.setMessage("Active banner " + id);
    b.setEnabled(true);
    return b;
  }

  private BannerRecord disabledBanner(String id) {
    BannerRecord b = new BannerRecord();
    b.setId(id);
    b.setMessage("Disabled banner " + id);
    b.setEnabled(false);
    return b;
  }

  private BannerRecord futureStartBanner(String id) {
    BannerRecord b = new BannerRecord();
    b.setId(id);
    b.setMessage("Future banner " + id);
    b.setEnabled(true);
    b.setStartTimestamp(System.currentTimeMillis() + 60_000);
    return b;
  }

  // ---------------------------------------------------------------------------
  // Constructor — initial cache warmup
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("constructor")
  class Constructor {

    @Test
    @DisplayName("warms the active cache from Redis on construction")
    void warmsCache() {
      when(repository.findAll()).thenReturn(List.of(activeBanner("a1"), disabledBanner("d1")));
      service = new GlobalBannerService(repository);

      List<BannerRecord> active = service.getActiveBanners();
      assertThat(active).extracting(BannerRecord::getId).containsExactly("a1");
    }

    @Test
    @DisplayName("handles empty Redis gracefully")
    void handlesEmpty() {
      when(repository.findAll()).thenReturn(List.of());
      service = new GlobalBannerService(repository);

      assertThat(service.getActiveBanners()).isEmpty();
    }
  }

  // ---------------------------------------------------------------------------
  // getActiveBanners — cache-only, no Redis hit
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("getActiveBanners")
  class GetActiveBanners {

    @BeforeEach
    void setUp() {
      when(repository.findAll()).thenReturn(List.of(activeBanner("a1"), disabledBanner("d1")));
      service = new GlobalBannerService(repository);
      // Reset so we can verify no further repository calls happen
      reset(repository);
    }

    @Test
    @DisplayName("returns only active banners from cache without touching Redis")
    void returnsActiveBannersFromCache() {
      List<BannerRecord> active = service.getActiveBanners();
      assertThat(active).extracting(BannerRecord::getId).containsExactly("a1");
      verifyNoInteractions(repository);
    }
  }

  // ---------------------------------------------------------------------------
  // refreshBannerData — replaces stale cache
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("refreshBannerData")
  class RefreshBannerData {

    @Test
    @DisplayName("replaces cache contents on refresh")
    void replacesCache() {
      when(repository.findAll()).thenReturn(List.of(activeBanner("old")));
      service = new GlobalBannerService(repository);
      assertThat(service.getActiveBanners()).extracting(BannerRecord::getId).containsExactly("old");

      when(repository.findAll()).thenReturn(List.of(activeBanner("new1"), activeBanner("new2")));
      service.refreshBannerData();

      assertThat(service.getActiveBanners())
          .extracting(BannerRecord::getId)
          .containsExactlyInAnyOrder("new1", "new2");
    }

    @Test
    @DisplayName("excludes disabled banners after refresh")
    void excludesDisabled() {
      when(repository.findAll())
          .thenReturn(List.of(activeBanner("a"), disabledBanner("d"), futureStartBanner("f")));
      service = new GlobalBannerService(repository);

      assertThat(service.getActiveBanners()).extracting(BannerRecord::getId).containsExactly("a");
    }

    @Test
    @DisplayName("does not propagate exceptions — leaves existing cache intact")
    void toleratesRepositoryError() {
      when(repository.findAll()).thenReturn(List.of(activeBanner("stable")));
      service = new GlobalBannerService(repository);

      when(repository.findAll()).thenThrow(new RuntimeException("Redis down"));
      service.refreshBannerData(); // must not throw

      // findAll() throws before activeCache.clear() is reached, so the stale
      // cache is preserved and still serves banners during the Redis outage.
      assertThat(service.getActiveBanners())
          .extracting(BannerRecord::getId)
          .containsExactly("stable");
    }
  }

  // ---------------------------------------------------------------------------
  // getBannerById
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("getBannerById")
  class GetBannerById {

    @BeforeEach
    void setUp() {
      when(repository.findAll()).thenReturn(List.of(activeBanner("cached")));
      service = new GlobalBannerService(repository);
      reset(repository);
    }

    @Test
    @DisplayName("returns cached active banner without a Redis call")
    void returnsFromCache() {
      Optional<BannerRecord> result = service.getBannerById("cached");
      assertThat(result).isPresent();
      assertThat(result.get().getId()).isEqualTo("cached");
      verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("falls back to Redis for banners not in the active cache")
    void fallsBackToRedis() {
      BannerRecord disabled = disabledBanner("db-only");
      when(repository.findById("db-only")).thenReturn(Optional.of(disabled));

      Optional<BannerRecord> result = service.getBannerById("db-only");
      assertThat(result).isPresent();
      assertThat(result.get().getId()).isEqualTo("db-only");
      verify(repository).findById("db-only");
    }

    @Test
    @DisplayName("returns empty when not in cache or Redis")
    void returnsEmpty() {
      when(repository.findById("ghost")).thenReturn(Optional.empty());
      assertThat(service.getBannerById("ghost")).isEmpty();
    }
  }

  // ---------------------------------------------------------------------------
  // save
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("save")
  class Save {

    @BeforeEach
    void setUp() {
      when(repository.findAll()).thenReturn(List.of());
      service = new GlobalBannerService(repository);
      reset(repository);
    }

    @Test
    @DisplayName("persists to Redis and adds active banner to cache")
    void persistsAndCaches() {
      BannerRecord b = activeBanner("new");
      service.save(b);

      verify(repository).save(b);
      assertThat(service.getActiveBanners()).extracting(BannerRecord::getId).containsExactly("new");
    }

    @Test
    @DisplayName("does not add disabled banner to active cache")
    void disabledNotCached() {
      BannerRecord b = disabledBanner("off");
      service.save(b);

      verify(repository).save(b);
      assertThat(service.getActiveBanners()).isEmpty();
    }

    @Test
    @DisplayName("sets createdAt on first save")
    void setsCreatedAt() {
      BannerRecord b = activeBanner("ts");
      assertThat(b.getCreatedAt()).isNull();
      service.save(b);
      assertThat(b.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("preserves createdAt on subsequent saves")
    void preservesCreatedAt() {
      BannerRecord b = activeBanner("ts2");
      b.setCreatedAt("2026-01-01T00:00:00Z");
      service.save(b);
      assertThat(b.getCreatedAt()).isEqualTo("2026-01-01T00:00:00Z");
    }

    @Test
    @DisplayName("always sets updatedAt")
    void setsUpdatedAt() {
      BannerRecord b = activeBanner("ts3");
      service.save(b);
      assertThat(b.getUpdatedAt()).isNotNull();
    }
  }

  // ---------------------------------------------------------------------------
  // delete
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("delete")
  class Delete {

    @BeforeEach
    void setUp() {
      when(repository.findAll()).thenReturn(List.of(activeBanner("del")));
      service = new GlobalBannerService(repository);
      reset(repository);
    }

    @Test
    @DisplayName("returns true, removes from Redis and cache when banner exists")
    void deletesExisting() {
      when(repository.delete("del")).thenReturn(true);
      assertThat(service.delete("del")).isTrue();
      verify(repository).delete("del");
      assertThat(service.getActiveBanners()).isEmpty();
    }

    @Test
    @DisplayName("returns false when banner does not exist in Redis")
    void returnsFalseWhenAbsent() {
      when(repository.delete("ghost")).thenReturn(false);
      assertThat(service.delete("ghost")).isFalse();
    }
  }

  // ---------------------------------------------------------------------------
  // deleteAll
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("deleteAll")
  class DeleteAll {

    @BeforeEach
    void setUp() {
      when(repository.findAll()).thenReturn(List.of(activeBanner("x1"), activeBanner("x2")));
      service = new GlobalBannerService(repository);
      reset(repository);
    }

    @Test
    @DisplayName("deletes all from Redis, clears cache, returns count")
    void deletesAll() {
      when(repository.deleteAll()).thenReturn(2);
      int count = service.deleteAll();
      assertThat(count).isEqualTo(2);
      verify(repository).deleteAll();
      assertThat(service.getActiveBanners()).isEmpty();
    }
  }
}
