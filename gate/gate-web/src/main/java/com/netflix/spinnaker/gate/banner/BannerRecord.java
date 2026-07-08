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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Data;

/**
 * Persistent model for a global UI banner stored in Redis.
 *
 * <p>Each banner is stored as a JSON-serialised {@code BannerRecord} under the key {@code
 * {prefix}:{id}}. The {@link #isActiveAt} method determines whether the banner should be surfaced
 * to users at a given instant, based on optional {@code startTimestamp}/{@code endTimestamp}
 * windows and the explicit {@code enabled} flag.
 */
@Data
@JsonInclude(Include.NON_NULL)
public class BannerRecord {

  /** Caller-supplied unique identifier (required, max 64 chars). */
  private String id;

  /**
   * HTML-safe message to display in the UI (required, max controlled by GlobalBannerProperties).
   */
  private String message;

  /** CSS colour value for the banner text (e.g. {@code #333333}). */
  private String color;

  /** CSS colour value for the banner background (e.g. {@code #fff3cd}). */
  private String backgroundColor;

  /** Whether the banner is administratively enabled. */
  private boolean enabled;

  /** ISO-8601 instant at which this record was first created. */
  private String createdAt;

  /** ISO-8601 instant at which this record was last modified. */
  private String updatedAt;

  /**
   * Optional Unix-epoch milliseconds at which the banner becomes active. When {@code null} the
   * banner is active from the moment it is created (subject to {@link #enabled}).
   */
  private Long startTimestamp;

  /**
   * Optional Unix-epoch milliseconds at which the banner automatically deactivates. When {@code
   * null} the banner never auto-expires.
   */
  private Long endTimestamp;

  /**
   * Returns {@code true} when this banner should be displayed to users at {@code currentTimeMs}.
   *
   * <p>A banner is active when:
   *
   * <ol>
   *   <li>{@link #enabled} is {@code true}
   *   <li>{@code currentTimeMs} is at or after {@link #startTimestamp} (if set)
   *   <li>{@code currentTimeMs} is before {@link #endTimestamp} (if set)
   * </ol>
   */
  public boolean isActiveAt(long currentTimeMs) {
    if (!enabled) {
      return false;
    }
    if (startTimestamp != null && currentTimeMs < startTimestamp) {
      return false;
    }
    if (endTimestamp != null && currentTimeMs > endTimestamp) {
      return false;
    }
    return true;
  }
}
