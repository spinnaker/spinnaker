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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for managing global UI banners.
 *
 * <p>{@code GET /banners} is intentionally unauthenticated — Deck polls it on every page load and
 * requiring a session would add unnecessary latency and couple the banner display to auth state.
 * All mutation endpoints require Fiat admin.
 */
@RestController
@RequestMapping("/banners")
@ConditionalOnProperty("global-banner.enabled")
@AllArgsConstructor
@Slf4j
public class GlobalBannerController {

  private final GlobalBannerService globalBannerService;
  private final GlobalBannerProperties properties;

  // ---------------------------------------------------------------------------
  // Public read (no auth required — polled by Deck on every page load)
  // ---------------------------------------------------------------------------

  @Operation(
      summary = "Get active banners",
      description =
          "Returns all banners that are currently active. This endpoint is public so Deck can"
              + " display banners without requiring a session.")
  @GetMapping
  public ResponseEntity<List<BannerRecord>> getActiveBanners() {
    return ResponseEntity.ok(globalBannerService.getActiveBanners());
  }

  // ---------------------------------------------------------------------------
  // Admin reads
  // ---------------------------------------------------------------------------

  @Operation(
      summary = "Get all banners (admin)",
      description = "Returns all banners including disabled ones. Requires Fiat admin.")
  @GetMapping("/all")
  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  public ResponseEntity<List<BannerRecord>> getAllBanners() {
    return ResponseEntity.ok(globalBannerService.getAllBanners());
  }

  @Operation(
      summary = "Get banner by id (admin)",
      description = "Returns a specific banner by id. Requires Fiat admin.")
  @GetMapping("/{id}")
  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  public ResponseEntity<BannerRecord> getBannerById(
      @Parameter(description = "Banner id") @PathVariable String id) {
    Optional<BannerRecord> banner = globalBannerService.getBannerById(id);
    return banner.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
  }

  // ---------------------------------------------------------------------------
  // Admin writes
  // ---------------------------------------------------------------------------

  @Operation(
      summary = "Create or update a banner (admin)",
      description =
          "Creates or updates the banner with the given id. Both id and message are required."
              + " Requires Fiat admin.")
  @PutMapping
  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  public ResponseEntity<BannerRecord> saveBanner(@RequestBody BannerRecord bannerRecord) {
    if (bannerRecord.getId() == null || bannerRecord.getId().isBlank()) {
      log.warn("Attempt to save banner with missing id");
      return ResponseEntity.badRequest().build();
    }
    if (bannerRecord.getMessage() == null || bannerRecord.getMessage().isBlank()) {
      log.warn("Attempt to save banner '{}' with missing message", bannerRecord.getId());
      return ResponseEntity.badRequest().build();
    }
    if (bannerRecord.getMessage().length() > properties.getMaxMessageLength()) {
      log.warn(
          "Attempt to save banner '{}' with message exceeding {} chars",
          bannerRecord.getId(),
          properties.getMaxMessageLength());
      return ResponseEntity.badRequest().build();
    }
    return ResponseEntity.ok(globalBannerService.save(bannerRecord));
  }

  @Operation(
      summary = "Update a banner by id (admin)",
      description =
          "Updates the banner with the given id. Returns 404 if the banner does not exist."
              + " Requires Fiat admin.")
  @PostMapping("/{id}")
  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  public ResponseEntity<BannerRecord> updateBanner(
      @Parameter(description = "Banner id") @PathVariable String id,
      @RequestBody BannerRecord bannerRecord) {
    Optional<BannerRecord> existing = globalBannerService.getBannerById(id);
    if (existing.isEmpty()) {
      log.warn("Attempt to update non-existent banner id='{}'", id);
      return ResponseEntity.notFound().build();
    }
    if (bannerRecord.getMessage() == null || bannerRecord.getMessage().isBlank()) {
      log.warn("Attempt to update banner '{}' with missing message", id);
      return ResponseEntity.badRequest().build();
    }
    if (bannerRecord.getMessage().length() > properties.getMaxMessageLength()) {
      log.warn(
          "Attempt to update banner '{}' with message exceeding {} chars",
          id,
          properties.getMaxMessageLength());
      return ResponseEntity.badRequest().build();
    }
    // Ensure the path variable id always wins over any id in the request body.
    bannerRecord.setId(id);
    // Preserve createdAt from the existing record.
    bannerRecord.setCreatedAt(existing.get().getCreatedAt());
    return ResponseEntity.ok(globalBannerService.save(bannerRecord));
  }

  @Operation(
      summary = "Delete a banner by id (admin)",
      description =
          "Deletes the banner with the given id. Returns 404 if it does not exist."
              + " Requires Fiat admin.")
  @DeleteMapping("/{id}")
  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  public ResponseEntity<Void> deleteBanner(
      @Parameter(description = "Banner id") @PathVariable String id) {
    boolean deleted = globalBannerService.delete(id);
    return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
  }

  @Operation(
      summary = "Delete all banners (admin)",
      description =
          "Deletes all banners. The number of deleted banners is returned in the"
              + " X-Deleted-Count response header. Requires Fiat admin.")
  @DeleteMapping
  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  public ResponseEntity<Void> deleteAllBanners() {
    int count = globalBannerService.deleteAll();
    return ResponseEntity.noContent().header("X-Deleted-Count", String.valueOf(count)).build();
  }

  @Operation(
      summary = "Force cache refresh (admin)",
      description =
          "Forces an immediate refresh of the active-banner cache from Redis without waiting for"
              + " the next scheduled interval. Requires Fiat admin.")
  @PostMapping("/refresh")
  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  public ResponseEntity<Map<String, String>> forceRefresh() {
    globalBannerService.forceRefresh();
    return ResponseEntity.ok(Map.of("status", "ok", "message", "Banner cache refreshed"));
  }
}
