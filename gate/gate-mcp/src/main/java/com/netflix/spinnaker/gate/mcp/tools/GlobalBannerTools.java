/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.gate.mcp.tools;

import com.netflix.spinnaker.gate.banner.BannerRecord;
import com.netflix.spinnaker.gate.banner.GlobalBannerProperties;
import com.netflix.spinnaker.gate.banner.GlobalBannerService;
import com.netflix.spinnaker.gate.mcp.support.McpAccessGuard;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.List;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Fiat-admin-only MCP tools for the global UI banner subsystem, mirroring gate-web's {@code
 * GlobalBannerController}.
 *
 * <p>{@link GlobalBannerService}/{@code RedisBannerRepository}/{@link BannerRecord} were moved from
 * gate-web to gate-core (same reasoning as {@code KayentaService}'s earlier move) so this module
 * could depend on them directly - gate-mcp can't depend on gate-web's controllers. Since {@code
 * GlobalBannerService} has no Fiat awareness of its own (gate-web's controller checks admin status
 * manually via {@code PermissionService.isAdmin}, not a Spring Security annotation), every method
 * here carries the same {@code @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")} gate-web's
 * controller enforces - required, not inherited.
 *
 * <p>Only registered when the global banner subsystem is enabled ({@code global-banner.enabled:
 * true}) - see the {@code @ConditionalOnProperty} guard in {@code McpServerAutoConfiguration}.
 */
public class GlobalBannerTools {

  private final GlobalBannerService globalBannerService;
  private final GlobalBannerProperties properties;
  private final McpAccessGuard accessGuard;

  public GlobalBannerTools(
      GlobalBannerService globalBannerService,
      GlobalBannerProperties properties,
      McpAccessGuard accessGuard) {
    this.globalBannerService = globalBannerService;
    this.properties = properties;
    this.accessGuard = accessGuard;
  }

  @McpTool(
      name = "list_global_banners",
      description =
          "Admin-only: list every global UI banner (active and inactive). Use this to find a banner's id before "
              + "calling set_global_banner (to update it) or delete_global_banner.")
  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  public List<BannerRecord> listGlobalBanners() {
    return globalBannerService.getAllBanners();
  }

  @McpTool(
      name = "set_global_banner",
      description =
          "Admin-only: create or update a global UI banner shown across all Deck pages. Creates a new banner if "
              + "'id' doesn't match an existing one, otherwise updates it in place (existing 'createdAt' is "
              + "preserved). Set 'enabled' to false to save a banner without displaying it yet.")
  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  public BannerRecord setGlobalBanner(
      @McpToolParam(
              description = "Unique banner id - create a new one, or update an existing id",
              required = true)
          String id,
      @McpToolParam(description = "Banner message text", required = true) String message,
      @McpToolParam(description = "Whether the banner is active", required = true) boolean enabled,
      @McpToolParam(description = "CSS color for the banner text, e.g. '#333333'", required = false)
          String color,
      @McpToolParam(
              description = "CSS color for the banner background, e.g. '#fff3cd'",
              required = false)
          String backgroundColor,
      @McpToolParam(
              description = "CSS font-size for the banner text, e.g. '14px'",
              required = false)
          String fontSize,
      @McpToolParam(
              description =
                  "Unix-epoch milliseconds the banner becomes active; omit for immediately",
              required = false)
          Long startTimestamp,
      @McpToolParam(
              description = "Unix-epoch milliseconds the banner auto-deactivates; omit for never",
              required = false)
          Long endTimestamp) {
    accessGuard.requireWriteAccess("set_global_banner", id);

    if (message.isBlank()) {
      throw new IllegalArgumentException("message must not be blank");
    }
    if (message.length() > properties.getMaxMessageLength()) {
      throw new IllegalArgumentException(
          "message exceeds max length of " + properties.getMaxMessageLength() + " characters");
    }

    BannerRecord existing = globalBannerService.getBannerById(id).orElse(null);

    BannerRecord record = new BannerRecord();
    record.setId(id);
    record.setMessage(message);
    record.setEnabled(enabled);
    record.setColor(color);
    record.setBackgroundColor(backgroundColor);
    record.setFontSize(fontSize);
    record.setStartTimestamp(startTimestamp);
    record.setEndTimestamp(endTimestamp);
    if (existing != null) {
      record.setCreatedAt(existing.getCreatedAt());
    }

    return globalBannerService.save(record);
  }

  @McpTool(
      name = "delete_global_banner",
      description = "Admin-only: delete a global UI banner by id.")
  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  public void deleteGlobalBanner(
      @McpToolParam(description = "Banner id to delete", required = true) String id) {
    accessGuard.requireWriteAccess("delete_global_banner", id);

    if (!globalBannerService.delete(id)) {
      throw new NotFoundException("Banner not found (id: " + id + ")");
    }
  }
}
