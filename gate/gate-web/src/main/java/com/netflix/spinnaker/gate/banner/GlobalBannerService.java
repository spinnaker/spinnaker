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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Business logic for the global banner subsystem.
 *
 * <p>Maintains an in-process {@link ConcurrentHashMap} of currently active banners so that the read
 * hot-path ({@link #getActiveBanners()}) never blocks on Redis. The cache is refreshed periodically
 * by a {@link Scheduled} task and on every write operation.
 *
 * <p>Redis is the source of truth: the {@code enabled} field is <em>never</em> written back to
 * Redis during a refresh — only the in-process view is updated based on time-window evaluation.
 */
@Slf4j
public class GlobalBannerService {

  private final RedisBannerRepository repository;
  private final ConcurrentMap<String, BannerRecord> activeCache = new ConcurrentHashMap<>();

  public GlobalBannerService(RedisBannerRepository repository) {
    this.repository = repository;
    refreshBannerData();
  }

  // ---------------------------------------------------------------------------
  // Scheduled cache refresh
  // ---------------------------------------------------------------------------

  /**
   * Refresh the in-process active-banner cache from Redis. Only banners that pass {@link
   * BannerRecord#isActiveAt} at the current instant are placed in the cache. Redis is not mutated.
   */
  @Scheduled(fixedRateString = "${global-banner.refresh-interval-ms:60000}")
  public void refreshBannerData() {
    log.debug("Refreshing global banner cache from Redis");
    try {
      List<BannerRecord> all = repository.findAll();
      long now = System.currentTimeMillis();
      ConcurrentMap<String, BannerRecord> next = new ConcurrentHashMap<>();
      for (BannerRecord banner : all) {
        if (banner.isActiveAt(now)) {
          next.put(banner.getId(), banner);
        }
      }
      activeCache.clear();
      activeCache.putAll(next);
      log.info("Global banner cache refreshed: {} active banner(s)", activeCache.size());
    } catch (Exception e) {
      log.error("Error refreshing global banner cache from Redis", e);
    }
  }

  // ---------------------------------------------------------------------------
  // Read operations
  // ---------------------------------------------------------------------------

  /**
   * Returns all currently active banners from the in-process cache. This is the fast path called on
   * every Deck page load — it never touches Redis.
   */
  public List<BannerRecord> getActiveBanners() {
    return List.copyOf(activeCache.values());
  }

  /**
   * Returns all banners (active and inactive) by reading directly from Redis. Used by admin
   * endpoints only.
   */
  public List<BannerRecord> getAllBanners() {
    return repository.findAll();
  }

  /** Returns a specific banner by id, checking the active cache first and falling back to Redis. */
  public Optional<BannerRecord> getBannerById(String id) {
    BannerRecord cached = activeCache.get(id);
    if (cached != null) {
      return Optional.of(cached);
    }
    return repository.findById(id);
  }

  // ---------------------------------------------------------------------------
  // Write operations
  // ---------------------------------------------------------------------------

  /**
   * Persist a banner to Redis and update the active cache.
   *
   * @return the saved record
   */
  public BannerRecord save(BannerRecord record) {
    String now = Instant.now().toString();
    if (record.getCreatedAt() == null) {
      record.setCreatedAt(now);
    }
    record.setUpdatedAt(now);

    repository.save(record);
    log.info("Saved banner id='{}' message='{}'", record.getId(), record.getMessage());

    if (record.isActiveAt(System.currentTimeMillis())) {
      activeCache.put(record.getId(), record);
    } else {
      activeCache.remove(record.getId());
    }
    return record;
  }

  /**
   * Delete a banner by id.
   *
   * @return {@code true} if the banner existed and was deleted
   */
  public boolean delete(String id) {
    boolean deleted = repository.delete(id);
    if (deleted) {
      activeCache.remove(id);
      log.info("Deleted banner id='{}'", id);
    }
    return deleted;
  }

  /**
   * Delete all banners.
   *
   * @return number of banners deleted
   */
  public int deleteAll() {
    int count = repository.deleteAll();
    activeCache.clear();
    log.info("Deleted all banners ({})", count);
    return count;
  }

  /** Forces an immediate cache refresh without waiting for the next scheduled interval. */
  public void forceRefresh() {
    log.info("Forcing global banner cache refresh");
    refreshBannerData();
  }
}
