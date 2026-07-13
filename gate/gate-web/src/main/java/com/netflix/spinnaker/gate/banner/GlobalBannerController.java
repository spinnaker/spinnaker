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

import com.netflix.spinnaker.gate.security.SpinnakerUser;
import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.security.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST API for managing global UI banners.
 *
 * <p>{@code GET /banners} requires authentication but no specific role — any logged-in user can
 * fetch active banners. All mutation endpoints require Fiat admin.
 */
@RestController
@RequestMapping("/banners")
@ConditionalOnProperty("global-banner.enabled")
@RequiredArgsConstructor
@Slf4j
public class GlobalBannerController {

  private final GlobalBannerService globalBannerService;
  private final GlobalBannerProperties properties;
  private final PermissionService permissionService;

  // ---------------------------------------------------------------------------
  // Read — any authenticated user
  // ---------------------------------------------------------------------------

  @Operation(summary = "Get active banners")
  @GetMapping
  public ResponseEntity<List<BannerRecord>> getActiveBanners() {
    return ResponseEntity.ok(globalBannerService.getActiveBanners());
  }

  // ---------------------------------------------------------------------------
  // Admin reads
  // ---------------------------------------------------------------------------

  @Operation(summary = "Get all banners (admin)")
  @GetMapping("/all")
  public ResponseEntity<List<BannerRecord>> getAllBanners(
      @Parameter(hidden = true) @SpinnakerUser User user) {
    requireAdmin(user);
    return ResponseEntity.ok(globalBannerService.getAllBanners());
  }

  @Operation(summary = "Get banner by id (admin)")
  @GetMapping("/{id}")
  public ResponseEntity<BannerRecord> getBannerById(
      @Parameter(hidden = true) @SpinnakerUser User user,
      @Parameter(description = "Banner id") @PathVariable String id) {
    requireAdmin(user);
    Optional<BannerRecord> banner = globalBannerService.getBannerById(id);
    return banner.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
  }

  // ---------------------------------------------------------------------------
  // Admin writes
  // ---------------------------------------------------------------------------

  @Operation(summary = "Create or update a banner (admin)")
  @PutMapping
  public ResponseEntity<BannerRecord> saveBanner(
      @Parameter(hidden = true) @SpinnakerUser User user, @RequestBody BannerRecord bannerRecord) {
    requireAdmin(user);
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

  @Operation(summary = "Update a banner by id (admin)")
  @PostMapping("/{id}")
  public ResponseEntity<BannerRecord> updateBanner(
      @Parameter(hidden = true) @SpinnakerUser User user,
      @Parameter(description = "Banner id") @PathVariable String id,
      @RequestBody BannerRecord bannerRecord) {
    requireAdmin(user);
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
    bannerRecord.setId(id);
    bannerRecord.setCreatedAt(existing.get().getCreatedAt());
    return ResponseEntity.ok(globalBannerService.save(bannerRecord));
  }

  @Operation(summary = "Delete a banner by id (admin)")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteBanner(
      @Parameter(hidden = true) @SpinnakerUser User user,
      @Parameter(description = "Banner id") @PathVariable String id) {
    requireAdmin(user);
    boolean deleted = globalBannerService.delete(id);
    return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
  }

  @Operation(summary = "Delete all banners (admin)")
  @DeleteMapping
  public ResponseEntity<Void> deleteAllBanners(@Parameter(hidden = true) @SpinnakerUser User user) {
    requireAdmin(user);
    int count = globalBannerService.deleteAll();
    return ResponseEntity.noContent().header("X-Deleted-Count", String.valueOf(count)).build();
  }

  @Operation(summary = "Force cache refresh (admin)")
  @PostMapping("/refresh")
  public ResponseEntity<Map<String, String>> forceRefresh(
      @Parameter(hidden = true) @SpinnakerUser User user) {
    requireAdmin(user);
    globalBannerService.forceRefresh();
    return ResponseEntity.ok(Map.of("status", "ok", "message", "Banner cache refreshed"));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private void requireAdmin(User user) {
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }
    if (!permissionService.isAdmin(user.getUsername())) {
      throw new AccessDeniedException("Only admins may manage global banners");
    }
  }
}
